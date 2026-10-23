"""T11 (D11.2): trades straight from IBKR's Flex Web Service, on demand, for the CSV export.

Two steps: SendRequest (token + query ID + date range) returns a reference code; GetStatement with that code
returns the report once IBKR has prepared it ("generation in progress" → ask again). At most 365 days per
request. The token comes from the app's Keychain over the control channel and is never logged.
"""

import asyncio
import logging
import xml.etree.ElementTree as ET
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from zoneinfo import ZoneInfo

import httpx

log = logging.getLogger(__name__)

BASE_URL = "https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService"
MAX_DAYS = 365
IN_PROGRESS = "1019"  # Statement generation in progress
INVALID_REQUEST = "1020"  # also what IBKR answers when it doesn't take the fd / td date range
NOT_AVAILABLE = "1003"  # no statement for these days (e.g. before the account existed, or today)
TOO_MANY = "1018"  # IBKR allows about 1 request a second and 10 a minute per token
REQUEST_GAP = 1.5  # seconds between our own requests
EASTERN = ZoneInfo("America/New_York")  # Flex times are in the account's time zone (US Eastern by default)


class FlexError(Exception):
    pass


class FlexNotSetUp(FlexError):
    pass


def _chunks(start: date, end: date) -> list[tuple[date, date]]:
    chunks, day = [], start
    while day <= end:
        last = min(end, day + timedelta(days=MAX_DAYS - 1))
        chunks.append((day, last))
        day = last + timedelta(days=1)
    return chunks


def _date(text: str) -> date:
    return datetime.strptime(text.replace("-", "")[:8], "%Y%m%d").date()


def _time(text: str, trade_date: date) -> datetime:
    """`20260110;153000` (or without time) in the account's zone."""
    digits = "".join(c for c in text if c.isdigit())
    if len(digits) >= 14:
        local = datetime.strptime(digits[:14], "%Y%m%d%H%M%S")
    else:
        local = datetime(trade_date.year, trade_date.month, trade_date.day, 12)
    return local.replace(tzinfo=EASTERN)


def _dec(text: str | None) -> Decimal:
    try:
        return Decimal(text or "0")
    except InvalidOperation:
        return Decimal(0)


def parse_trades(xml: bytes) -> list[dict]:
    """Flex `<Trade>` rows → fill-like dicts (as in the trade history) with an explicit `trade_date`."""
    root = ET.fromstring(xml)
    trades = root.iter("Trade")
    rows = [t.attrib for t in trades]
    if any(r.get("levelOfDetail", "EXECUTION").upper() == "EXECUTION" for r in rows):
        rows = [r for r in rows if r.get("levelOfDetail", "EXECUTION").upper() == "EXECUTION"]
    fills = []
    for r in rows:
        side = (r.get("buySell") or "").upper()
        if side not in ("BUY", "SELL"):
            continue  # "BUY (Ca.)" / "SELL (Ca.)" = cancelled trades
        trade_date = _date(r.get("tradeDate") or r.get("dateTime", ""))
        fills.append({
            "exec_id": r.get("ibExecID") or r.get("tradeID") or "",
            "order_id": r.get("ibOrderID") or None,
            "order_ref": r.get("orderReference") or None,
            "side": side,
            "quantity": str(abs(_dec(r.get("quantity")))),
            "price": str(_dec(r.get("tradePrice"))),
            "commission": str(abs(_dec(r.get("ibCommission")))),
            "time": _time(r.get("dateTime", ""), trade_date).isoformat(),
            "trade_date": trade_date.isoformat(),
            "contract": {"conid": int(r.get("conid") or 0), "symbol": r.get("symbol", "")},
        })
    return fills


class FlexClient:
    def __init__(self, base_url: str = BASE_URL, transport: httpx.AsyncBaseTransport | None = None,
                 poll_interval: float = 6.0, timeout: float = 120.0, request_gap: float = REQUEST_GAP) -> None:
        self._base = base_url
        self._transport = transport
        self._poll = poll_interval
        self._timeout = timeout
        self._gap = request_gap
        self._token: str | None = None
        self._query_id: str | None = None

    def set_credentials(self, token: str, query_id: str) -> None:
        self._token, self._query_id = token.strip(), query_id.strip()

    @property
    def configured(self) -> bool:
        return bool(self._token and self._query_id)

    async def trades(self, start: date, end: date) -> list[dict]:
        if not self.configured:
            raise FlexNotSetUp("Set up the IBKR Flex query in Settings")
        fills = []
        async with httpx.AsyncClient(transport=self._transport, timeout=30,
                                     headers={"User-Agent": "TraderTerminal/1.0"}) as client:
            for first, last in _chunks(start, end):
                try:
                    try:
                        report = await self._statement(client, first, last)
                    except FlexError as error:
                        if f"({INVALID_REQUEST})" not in str(error):
                            raise
                        # IBKR didn't take the date range: the query's own period (last 365 days), filtered below.
                        await asyncio.sleep(self._gap)
                        report = await self._statement(client, None, None)
                except FlexError as error:
                    if f"({NOT_AVAILABLE})" in str(error):
                        continue  # nothing IBKR can report for these days (e.g. a new account): no transactions
                    if f"({TOO_MANY})" in str(error):
                        raise FlexError("IBKR limits how often Flex can be asked — try again in a minute") from None
                    raise
                fills += parse_trades(report)
        return [f for f in fills if start <= date.fromisoformat(f["trade_date"]) <= end]

    async def _statement(self, client: httpx.AsyncClient, first: date | None, last: date | None) -> bytes:
        params = {"t": self._token, "q": self._query_id, "v": "3"}
        if first and last:
            params |= {"fd": first.strftime("%Y%m%d"), "td": last.strftime("%Y%m%d")}
        root = ET.fromstring((await client.get(f"{self._base}/SendRequest", params=params)).content)
        if root.findtext("Status") != "Success":
            raise FlexError(self._message(root, "request"))
        code, url = root.findtext("ReferenceCode"), root.findtext("Url") or f"{self._base}/GetStatement"
        deadline = asyncio.get_running_loop().time() + self._timeout
        refused = 0
        while True:
            # IBKR needs a moment after SendRequest; asked too early it answers "in progress" or even 1020.
            await asyncio.sleep(self._poll)
            body = (await client.get(url, params={"t": self._token, "q": code, "v": "3"})).content
            root = ET.fromstring(body)
            if root.tag != "FlexStatementResponse":
                return body  # the report itself (FlexQueryResponse)
            refused += root.findtext("ErrorCode") == INVALID_REQUEST
            if root.findtext("ErrorCode") not in (IN_PROGRESS, INVALID_REQUEST) or refused >= 3:
                raise FlexError(self._message(root, "statement"))
            if asyncio.get_running_loop().time() > deadline:
                raise FlexError(self._message(root, "statement") if root.findtext("ErrorCode") == INVALID_REQUEST
                                else "IBKR is still preparing the report — try again in a minute")

    @staticmethod
    def _message(root: ET.Element, step: str) -> str:
        code, message = root.findtext("ErrorCode"), root.findtext("ErrorMessage")
        return f"IBKR Flex {step}: {message or 'failed'}" + (f" ({code})" if code else "")
