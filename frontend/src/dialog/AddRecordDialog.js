import {Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from "@mui/material";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import axios from "axios";
import {handleError} from "../service/utils";
import {validateNumber} from "../service/ValidationService";


const AddRecordDialog = props => {
    const {companyId, open, handleClose, indicators, assets} = props
    const [alert, setAlert] = useState(null)

    const [title, setTitle] = useState("")
    const [date, setDate] = useState("")
    const [price, setPrice] = useState("")

    const [priceToRevenues, setPriceToRevenues] = useState("")
    const [priceToGrossProfit, setPriceToGrossProfit] = useState("")
    const [priceToOperatingIncome, setPriceToOperatingIncome] = useState("")
    const [priceToNetIncome, setPriceToNetIncome] = useState("")
    const [dividendYield, setDividendYield] = useState("")
    const [sumAssetQuantity, setSumAssetQuantity] = useState("")
    const [avgAssetPrice, setAvgAssetPrice] = useState("")

    useEffect(() => {
        if (open) {
            setTitle("")

            setDate("")
            setPrice("")
            setPriceToRevenues("")
            setPriceToGrossProfit("")
            setPriceToOperatingIncome("")
            setPriceToNetIncome("")
            setDividendYield("")

            if (indicators) {
                setDate(`${indicators.datetime}`.split("T")[0])
                setPrice(`${indicators.price}`)

                if (indicators.ttm) {
                    setPriceToRevenues(`${indicators.ttm.marketCapToRevenues ?? ''}`)
                    setPriceToGrossProfit(`${indicators.ttm.marketCapToGrossProfit ?? ''}`)
                    setPriceToOperatingIncome(`${indicators.ttm.marketCapToOperatingIncome ?? ''}`)
                    setPriceToNetIncome(`${indicators.ttm.marketCapToNetIncome ?? ''}`)
                    setDividendYield(`${indicators.ttm.dividendYield ?? ''}`)
                }
            }

            if (assets) {
                setSumAssetQuantity(`${assets.aggregate.quantity}`)
                setAvgAssetPrice(`${assets.aggregate.purchasePrice}`)
            } else {
                setSumAssetQuantity("")
                setAvgAssetPrice("")
            }
        }
        // eslint-disable-next-line
    }, [open])

    function createRecord() {
        const data = {companyId: companyId, title: title, date: date, price: price,
            priceToRevenues: priceToRevenues, priceToGrossProfit: priceToGrossProfit,
            priceToOperatingIncome: priceToOperatingIncome, priceToNetIncome: priceToNetIncome,
            dividendYield: dividendYield, sumAssetQuantity: sumAssetQuantity, avgAssetPrice: avgAssetPrice}

        axios.post(backend + "/record", data)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(handleError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createRecord()},}}
        >
            <DialogTitle>Add Record</DialogTitle>
            <DialogContent>
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-date"
                           type="date"
                           value={date}
                           onChange={(e) => {setDate(e.target.value);setAlert(null);}}
                           error={date === ""}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-title"
                           value={title}
                           label="Title"
                           onChange={(e) => {setTitle(e.target.value);setAlert(null);}}
                           error={title === ""}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-price"
                           value={price}
                           label="Price"
                           onChange={(e) => {setPrice(e.target.value);setAlert(null);}}
                           error={validateNumber(price, false, 10, 4) !== ""}
                           helperText={validateNumber(price, false, 10, 4)}
                />

                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-ps"
                           value={priceToRevenues}
                           label="PS"
                           onChange={(e) => {setPriceToRevenues(e.target.value);setAlert(null);}}
                           error={validateNumber(priceToRevenues, true, 6, 2) !== ""}
                           helperText={validateNumber(priceToRevenues, true, 6, 2)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-pg"
                           value={priceToGrossProfit}
                           label="PG"
                           onChange={(e) => {setPriceToGrossProfit(e.target.value);setAlert(null);}}
                           error={validateNumber(priceToGrossProfit, true, 6, 2) !== ""}
                           helperText={validateNumber(priceToGrossProfit, true, 6, 2)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-po"
                           value={priceToOperatingIncome}
                           label="PO"
                           onChange={(e) => {setPriceToOperatingIncome(e.target.value);setAlert(null);}}
                           error={validateNumber(priceToOperatingIncome, true, 6, 2) !== ""}
                           helperText={validateNumber(priceToOperatingIncome, true, 6, 2)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-pe"
                           value={priceToNetIncome}
                           label="PE"
                           onChange={(e) => {setPriceToNetIncome(e.target.value);setAlert(null);}}
                           error={validateNumber(priceToNetIncome, true, 6, 2) !== ""}
                           helperText={validateNumber(priceToNetIncome, true, 6, 2)}
                />

                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-dy"
                           value={dividendYield}
                           label="DY"
                           onChange={(e) => {setDividendYield(e.target.value);setAlert(null);}}
                           error={validateNumber(dividendYield, true, 5, 2) !== ""}
                           helperText={validateNumber(dividendYield, true, 5, 2)}
                />

                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-assets-quantity"
                           value={sumAssetQuantity}
                           label="assets quantity sum"
                           onChange={(e) => {setSumAssetQuantity(e.target.value);setAlert(null);}}
                           error={validateNumber(sumAssetQuantity, true, 8, 4) !== ""}
                           helperText={validateNumber(sumAssetQuantity, true, 8, 4)}
                />
                <TextField required margin="dense" fullWidth variant="standard" id="trader-record-assets-price"
                           value={avgAssetPrice}
                           label="assets avg purchase price"
                           onChange={(e) => {setAvgAssetPrice(e.target.value);setAlert(null);}}
                           error={validateNumber(avgAssetPrice, true, 10, 4) !== ""}
                           helperText={validateNumber(avgAssetPrice, true, 10, 4)}
                />

            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    {alert}
                </Alert>
            }
            <DialogActions>
                <Button onClick={handleClose}>Cancel</Button>
                <Button type="submit">Create</Button>
            </DialogActions>
        </Dialog>
    )
}
export default AddRecordDialog