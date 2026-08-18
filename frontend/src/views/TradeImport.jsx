import {
    Alert,
    AlertTitle,
    Box,
    Button,
    Chip,
    CircularProgress,
    Paper,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from "@mui/material";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import FileUploadOutlinedIcon from "@mui/icons-material/FileUploadOutlined";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import axios from "axios";
import React, {useRef, useState} from "react";
import {backend} from "../properties";
import {formatError} from "../service/FormattingService";

export const TRADE_IMPORT_TEMPLATE = `date,type,ticker,quantity,price,fees,portfolio
2026-01-10,BUY,NVDA,5,145.50,4.95,PATRIA_STANDARD
2026-02-15,BUY,NVDA,3,152.25,4.95,PATRIA_STANDARD
2026-04-01,SELL,NVDA,8,180.00,6.95,PATRIA_STANDARD`;

const readFile = file => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error ?? new Error("Unable to read the selected file"));
    reader.readAsText(file);
});

const requestRows = rows => rows.map(row => ({
    rowNumber: row.rowNumber,
    date: row.date,
    type: row.type,
    ticker: row.ticker,
    quantity: row.quantity,
    price: row.price,
    fees: row.fees,
    portfolio: row.portfolio,
}));

export const TradeImport = () => {
    const fileInput = useRef(null);
    const [preview, setPreview] = useState(null);
    const [fileName, setFileName] = useState("");
    const [alert, setAlert] = useState(null);
    const [success, setSuccess] = useState("");
    const [loading, setLoading] = useState(false);
    const [importing, setImporting] = useState(false);
    const [dragging, setDragging] = useState(false);
    const [templateCopied, setTemplateCopied] = useState(false);

    async function copyTemplate() {
        try {
            if (!navigator.clipboard?.writeText) {
                throw new Error("Clipboard access is not available in this browser");
            }
            await navigator.clipboard.writeText(TRADE_IMPORT_TEMPLATE);
            setTemplateCopied(true);
            setAlert(null);
        } catch (error) {
            setAlert({title: "Template could not be copied", message: error.message});
        }
    }

    async function loadPreview(file) {
        if (!file) return;
        if (!file.name.toLowerCase().endsWith(".csv")) {
            setAlert({title: "Invalid file", message: "Select a .csv file."});
            return;
        }

        setFileName(file.name);
        setPreview(null);
        setSuccess("");
        setAlert(null);
        setLoading(true);
        try {
            const csv = await readFile(file);
            const response = await axios.post(backend + "/trade/import/preview", csv, {
                headers: {"Content-Type": "text/csv"},
            });
            setPreview(response.data);
        } catch (error) {
            setAlert(formatError(error));
        } finally {
            setLoading(false);
        }
    }

    function handleDrop(event) {
        event.preventDefault();
        setDragging(false);
        loadPreview(event.dataTransfer.files?.[0]);
    }

    async function importTrades() {
        if (!preview?.valid || importing) return;

        setImporting(true);
        setAlert(null);
        try {
            const response = await axios.post(backend + "/trade/import", {
                rows: requestRows(preview.rows),
            });
            const importedRows = response.data?.rows?.length ?? preview.rows.length;
            setSuccess(`${importedRows} trade${importedRows === 1 ? "" : "s"} imported successfully.`);
        } catch (error) {
            if (Array.isArray(error.response?.data?.errors)) {
                setPreview(error.response.data);
            }
            setAlert(formatError(error));
        } finally {
            setImporting(false);
        }
    }

    const invalidRows = new Set((preview?.errors ?? [])
        .map(error => error.rowNumber)
        .filter(rowNumber => rowNumber != null));

    return (
        <Box sx={{maxWidth: 1280, margin: "0 auto", paddingTop: {xs: 1, md: 3}, paddingBottom: 4}}>
            <Paper elevation={3} sx={{overflow: "hidden", borderTop: "5px solid #ed9b40"}}>
                <Box sx={{padding: {xs: 2, md: 3}, background: "linear-gradient(115deg, #fff8ec, #ffffff 60%)"}}>
                    <Typography variant="overline" sx={{letterSpacing: 2, color: "text.secondary"}}>
                        Admin / Trades
                    </Typography>
                    <Typography variant="h4" component="h1" sx={{fontWeight: 700, marginBottom: 1}}>
                        Bulk trade import
                    </Typography>
                    <Typography color="text.secondary" sx={{maxWidth: 780}}>
                        Upload chronologically recorded purchases and sales. The preview validates every row and shows
                        the FIFO lots each sale will consume before anything is written.
                    </Typography>

                    <Stack direction={{xs: "column", sm: "row"}} spacing={1.5} sx={{marginTop: 3}}>
                        <Button
                            variant="outlined"
                            startIcon={<ContentCopyIcon/>}
                            onClick={copyTemplate}
                        >
                            {templateCopied ? "Template copied" : "Copy CSV template"}
                        </Button>
                        <Button
                            variant="contained"
                            startIcon={<UploadFileIcon/>}
                            onClick={() => fileInput.current?.click()}
                        >
                            Choose CSV file
                        </Button>
                        <input
                            ref={fileInput}
                            type="file"
                            accept=".csv,text/csv"
                            hidden
                            onChange={event => {
                                loadPreview(event.target.files?.[0]);
                                event.target.value = "";
                            }}
                        />
                    </Stack>

                    <Box
                        role="button"
                        tabIndex={0}
                        aria-label="Drop trade CSV file"
                        onClick={() => fileInput.current?.click()}
                        onKeyDown={event => {
                            if (event.key === "Enter" || event.key === " ") fileInput.current?.click();
                        }}
                        onDragEnter={event => {event.preventDefault();setDragging(true);}}
                        onDragOver={event => event.preventDefault()}
                        onDragLeave={() => setDragging(false)}
                        onDrop={handleDrop}
                        sx={{
                            marginTop: 2.5,
                            padding: {xs: 2.5, md: 4},
                            border: "2px dashed",
                            borderColor: dragging ? "primary.main" : "divider",
                            borderRadius: 2,
                            textAlign: "center",
                            cursor: "pointer",
                            backgroundColor: dragging ? "action.hover" : "rgba(255,255,255,0.6)",
                            transition: "background-color 120ms ease, border-color 120ms ease",
                        }}
                    >
                        <FileUploadOutlinedIcon sx={{fontSize: 38, color: "text.secondary"}}/>
                        <Typography sx={{fontWeight: 600}}>
                            {fileName || "Drop a CSV file here"}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            Rows are sorted by date; original order is retained when dates match.
                        </Typography>
                    </Box>
                </Box>

                <Box sx={{padding: {xs: 2, md: 3}, paddingTop: 0}}>
                    {loading &&
                        <Stack alignItems="center" spacing={1.5} sx={{padding: 4}}>
                            <CircularProgress size={34}/>
                            <Typography color="text.secondary">Building FIFO preview...</Typography>
                        </Stack>
                    }

                    {alert &&
                        <Alert severity="error" sx={{marginTop: 2}}>
                            <AlertTitle>{alert.title}</AlertTitle>
                            {alert.message}
                        </Alert>
                    }
                    {success &&
                        <Alert severity="success" sx={{marginTop: 2}}>{success}</Alert>
                    }
                    {preview?.reordered &&
                        <Alert severity="warning" sx={{marginTop: 2}}>
                            The CSV rows were not chronological. The preview below shows their execution order.
                        </Alert>
                    }
                    {preview?.errors?.length > 0 &&
                        <Alert severity="error" variant="outlined" sx={{marginTop: 2}}>
                            <AlertTitle>Import cannot proceed</AlertTitle>
                            <Stack component="ul" spacing={0.5} sx={{margin: 0, paddingLeft: 2.5}}>
                                {preview.errors.map((error, index) => (
                                    <Typography component="li" variant="body2" key={`${error.rowNumber}-${error.field}-${index}`}>
                                        {error.rowNumber ? `Row ${error.rowNumber}, ` : ""}{error.field}: {error.message}
                                    </Typography>
                                ))}
                            </Stack>
                        </Alert>
                    }

                    {preview?.rows?.length > 0 &&
                        <>
                            <Stack
                                direction={{xs: "column", sm: "row"}}
                                justifyContent="space-between"
                                alignItems={{xs: "stretch", sm: "center"}}
                                spacing={1.5}
                                sx={{marginTop: 3, marginBottom: 1.5}}
                            >
                                <Box>
                                    <Typography variant="h6">Execution preview</Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        {preview.rows.length} parsed row{preview.rows.length === 1 ? "" : "s"}
                                    </Typography>
                                </Box>
                                <Button
                                    variant="contained"
                                    color="success"
                                    disabled={!preview.valid || importing || Boolean(success)}
                                    onClick={importTrades}
                                    startIcon={importing ? <CircularProgress size={16} color="inherit"/> : undefined}
                                >
                                    {importing ? "Importing..." : `Import ${preview.rows.length} trades`}
                                </Button>
                            </Stack>

                            <TableContainer sx={{overflowX: "auto", border: "1px solid", borderColor: "divider", borderRadius: 1}}>
                                <Table size="small" stickyHeader aria-label="Trade import preview">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>Row</TableCell>
                                            <TableCell>Date</TableCell>
                                            <TableCell>Type</TableCell>
                                            <TableCell>Ticker</TableCell>
                                            <TableCell align="right">Quantity</TableCell>
                                            <TableCell align="right">Price</TableCell>
                                            <TableCell align="right">Fees</TableCell>
                                            <TableCell>Portfolio</TableCell>
                                            <TableCell sx={{minWidth: 220}}>FIFO allocations</TableCell>
                                            <TableCell align="right">Remaining</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {preview.rows.map(row => (
                                            <TableRow
                                                key={row.rowNumber}
                                                sx={{backgroundColor: invalidRows.has(row.rowNumber) ? "rgba(211,47,47,0.06)" : undefined}}
                                            >
                                                <TableCell>{row.rowNumber}</TableCell>
                                                <TableCell sx={{whiteSpace: "nowrap"}}>{row.date}</TableCell>
                                                <TableCell>
                                                    <Chip
                                                        size="small"
                                                        label={row.type}
                                                        color={row.type === "BUY" ? "success" : "warning"}
                                                        variant="outlined"
                                                    />
                                                </TableCell>
                                                <TableCell sx={{fontWeight: 700}}>{row.ticker}</TableCell>
                                                <TableCell align="right">{row.quantity}</TableCell>
                                                <TableCell align="right">{row.price}</TableCell>
                                                <TableCell align="right">{row.fees}</TableCell>
                                                <TableCell>{row.portfolio}</TableCell>
                                                <TableCell>
                                                    {row.allocations?.length > 0
                                                        ? row.allocations.map((allocation, index) => (
                                                            <Typography variant="caption" component="div" key={`${allocation.source}-${index}`}>
                                                                {allocation.source}: {allocation.quantity} ({allocation.purchaseDate})
                                                            </Typography>
                                                        ))
                                                        : <Typography variant="caption" color="text.secondary">-</Typography>
                                                    }
                                                </TableCell>
                                                <TableCell align="right">{row.remainingQuantity ?? "-"}</TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </>
                    }
                </Box>
            </Paper>
        </Box>
    );
};
