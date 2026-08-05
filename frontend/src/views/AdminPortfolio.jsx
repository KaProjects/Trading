import React, {useMemo, useState} from "react";
import axios from "axios";
import {
    Alert,
    Box,
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    FormControl,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from "@mui/material";
import {backend} from "../properties";
import {useData} from "../service/BackendService";
import {formatDate, formatDecimals, formatError} from "../service/FormattingService";
import {Loader} from "./component/Loader";

export const AdminPortfolio = ({companyLists = {all: []}, portfolios = []}) => {
    const companies = companyLists.all ?? [];
    const [companyId, setCompanyId] = useState("");
    const [portfolioKey, setPortfolioKey] = useState("");
    const [selectedTradeIds, setSelectedTradeIds] = useState([]);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [refresh, setRefresh] = useState("");
    const [alert, setAlert] = useState(null);

    const query = "/admin/portfolio/trades?filter"
        + (companyId ? "&companyId=" + companyId : "")
        + (refresh ? "&refresh=" + refresh : "");
    const {data, loaded, error} = useData(query);
    const selectedPortfolio = useMemo(
        () => portfolios.find(portfolio => portfolio.key === portfolioKey),
        [portfolioKey, portfolios]
    );

    function changeCompany(value) {
        setCompanyId(value);
        setSelectedTradeIds([]);
        setAlert(null);
    }

    function toggleTrade(tradeId) {
        setSelectedTradeIds(current => current.includes(tradeId)
            ? current.filter(id => id !== tradeId)
            : [...current, tradeId]
        );
        setAlert(null);
    }

    function assignPortfolio() {
        axios.put(backend + "/admin/portfolio", {
            tradeIds: selectedTradeIds,
            portfolio: portfolioKey,
        }).then(() => {
            setConfirmOpen(false);
            setSelectedTradeIds([]);
            setAlert({severity: "success", message: "Portfolio assigned successfully."});
            setRefresh(Date.now().toString());
        }).catch(requestError => {
            const formatted = formatError(requestError);
            setConfirmOpen(false);
            setAlert({severity: "error", message: formatted.message, title: formatted.title});
        });
    }

    const trades = data ?? [];
    const assignmentDisabled = selectedTradeIds.length === 0 || !portfolioKey;

    return (
        <Box sx={{maxWidth: 1100, mx: "auto", mt: 2}}>
            <Typography variant="h5" sx={{mb: 2}}>Portfolio assignment</Typography>

            <Stack direction={{xs: "column", sm: "row"}} spacing={2} sx={{mb: 2}}>
                <FormControl variant="standard" sx={{minWidth: 220}}>
                    <InputLabel id="admin-portfolio-company-label">Company</InputLabel>
                    <Select
                        labelId="admin-portfolio-company-label"
                        value={companyId}
                        onChange={event => changeCompany(event.target.value)}
                    >
                        <MenuItem value="">All companies</MenuItem>
                        {companies.map(company => (
                            <MenuItem key={company.id} value={company.id}>{company.ticker}</MenuItem>
                        ))}
                    </Select>
                </FormControl>

                <FormControl variant="standard" sx={{minWidth: 220}}>
                    <InputLabel id="admin-portfolio-value-label">Portfolio</InputLabel>
                    <Select
                        labelId="admin-portfolio-value-label"
                        value={portfolioKey}
                        onChange={event => {setPortfolioKey(event.target.value);setAlert(null);}}
                    >
                        {portfolios.map(portfolio => (
                            <MenuItem key={portfolio.key} value={portfolio.key}>
                                {portfolio.name} ({portfolio.abbreviation})
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>

                <Button
                    variant="contained"
                    disabled={assignmentDisabled}
                    onClick={() => setConfirmOpen(true)}
                    sx={{alignSelf: {sm: "flex-end"}}}
                >
                    Assign portfolio
                </Button>
            </Stack>

            {alert && <Alert severity={alert.severity} sx={{mb: 2}}>{alert.message}</Alert>}
            {!loaded && <Loader error={error}/>}

            {loaded &&
                <TableContainer component={Paper} sx={{maxHeight: "calc(100vh - var(--main-bar-height, 48px) - 180px)"}}>
                    <Table size="small" stickyHeader aria-label="Trades without portfolio">
                        <TableHead>
                            <TableRow>
                                <TableCell padding="checkbox"></TableCell>
                                <TableCell>ID</TableCell>
                                <TableCell>Company</TableCell>
                                <TableCell>Purchase date</TableCell>
                                <TableCell align="right">Quantity</TableCell>
                                <TableCell align="right">Price</TableCell>
                                <TableCell>Sale date</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {trades.map(trade => (
                                <TableRow key={trade.id} hover selected={selectedTradeIds.includes(trade.id)}>
                                    <TableCell padding="checkbox">
                                        <Checkbox
                                            checked={selectedTradeIds.includes(trade.id)}
                                            onChange={() => toggleTrade(trade.id)}
                                            inputProps={{"aria-label": `Select trade ${trade.id}`}}
                                        />
                                    </TableCell>
                                    <TableCell>{trade.id}</TableCell>
                                    <TableCell>{trade.company.ticker}</TableCell>
                                    <TableCell>{formatDate(trade.purchaseDate)}</TableCell>
                                    <TableCell align="right">{trade.purchaseQuantity}</TableCell>
                                    <TableCell align="right">{formatDecimals(trade.purchasePrice, 0, 4)}</TableCell>
                                    <TableCell>{formatDate(trade.sellDate)}</TableCell>
                                </TableRow>
                            ))}
                            {trades.length === 0 &&
                                <TableRow>
                                    <TableCell colSpan={7} align="center">No trades without portfolio.</TableCell>
                                </TableRow>
                            }
                        </TableBody>
                    </Table>
                </TableContainer>
            }

            <Dialog open={confirmOpen} onClose={() => setConfirmOpen(false)}>
                <DialogTitle>Confirm portfolio assignment</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Assign {selectedPortfolio?.name} ({selectedPortfolio?.abbreviation}) to {selectedTradeIds.length} selected {selectedTradeIds.length === 1 ? "trade" : "trades"}?
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setConfirmOpen(false)}>Cancel</Button>
                    <Button variant="contained" onClick={assignPortfolio}>Confirm</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
