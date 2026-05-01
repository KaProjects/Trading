import {Alert, AlertTitle, Button, Dialog, DialogActions, DialogContent, DialogTitle} from "@mui/material";
import React, {useEffect, useState} from "react";
import {backend} from "../properties";
import axios from "axios";
import {validateNumber} from "../service/ValidationService";
import {formatError} from "../service/FormattingService";
import {DialogTextField} from "./component/DialogTextField";
import {DialogDatePicker} from "./component/DialogDatePicker";


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
    const [targets, setTargets] = useState("");

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
            setTargets("")

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

            if (assets.aggregate) {
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
        const nullIfBlank = (value) => value ? value : null
        const data = {companyId: companyId, title: title, date: date, price: price,
            priceToRevenues: nullIfBlank(priceToRevenues),
            priceToGrossProfit: nullIfBlank(priceToGrossProfit),
            priceToOperatingIncome: nullIfBlank(priceToOperatingIncome),
            priceToNetIncome: nullIfBlank(priceToNetIncome),
            dividendYield: nullIfBlank(dividendYield),
            sumAssetQuantity: nullIfBlank(sumAssetQuantity),
            avgAssetPrice: nullIfBlank(avgAssetPrice),
            targets: nullIfBlank(targets)}

        axios.post(backend + "/record", data)
            .then((response) => {
                props.triggerRefresh()
                handleClose()
            }).catch((error) => {setAlert(formatError(error))})
    }

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            PaperProps={{component: 'form', onSubmit: (event) => {event.preventDefault();createRecord()},}}
        >
            <DialogTitle>Add Record</DialogTitle>
            <DialogContent>
                <DialogDatePicker
                    id="trader-record-date"
                    value={date}
                    onChange={(e) => {setDate(e.target.value);setAlert(null);}}
                />
                <DialogTextField
                    id="trader-record-title"
                    value={title}
                    label="Title"
                    onChange={(e) => {setTitle(e.target.value);setAlert(null);}}
                    validate={() => title === "" ? "not filled" : ""}
                />
                <DialogTextField
                    id="trader-record-price"
                    value={price}
                    label="Price"
                    onChange={(e) => {setPrice(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(price, false, 10, 4, false)}
                />

                <DialogTextField
                    id="trader-record-ps"
                    value={priceToRevenues}
                    required={false}
                    label="PS"
                    onChange={(e) => {setPriceToRevenues(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(priceToRevenues, true, 6, 2, false)}
                />
                <DialogTextField
                    id="trader-record-pg"
                    value={priceToGrossProfit}
                    required={false}
                    label="PG"
                    onChange={(e) => {setPriceToGrossProfit(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(priceToGrossProfit, true, 6, 2, true)}
                />
                <DialogTextField
                    id="trader-record-po"
                    value={priceToOperatingIncome}
                    required={false}
                    label="PO"
                    onChange={(e) => {setPriceToOperatingIncome(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(priceToOperatingIncome, true, 6, 2, true)}
                />
                <DialogTextField
                    id="trader-record-pe"
                    value={priceToNetIncome}
                    required={false}
                    label="PE"
                    onChange={(e) => {setPriceToNetIncome(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(priceToNetIncome, true, 6, 2, true)}
                />

                <DialogTextField
                    id="trader-record-dy"
                    value={dividendYield}
                    required={false}
                    label="DY"
                    onChange={(e) => {setDividendYield(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(dividendYield, true, 5, 2, false)}
                />

                <DialogTextField
                    id="trader-record-assets-quantity"
                    value={sumAssetQuantity}
                    required={false}
                    label="assets quantity sum"
                    onChange={(e) => {setSumAssetQuantity(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(sumAssetQuantity, true, 8, 4, false)}
                />
                <DialogTextField
                    id="trader-record-assets-price"
                    value={avgAssetPrice}
                    required={false}
                    label="assets avg purchase price"
                    onChange={(e) => {setAvgAssetPrice(e.target.value);setAlert(null);}}
                    validate={() => validateNumber(avgAssetPrice, true, 10, 4, false)}
                />
                <DialogTextField
                    id="trader-record-targets"
                    value={targets}
                    required={false}
                    label="price targets"
                    onChange={(e) => {setTargets(e.target.value);setAlert(null);}}
                    validate={() => ""}
                />

            </DialogContent>
            {alert &&
                <Alert severity="error" variant="filled">
                    <AlertTitle>{alert.title}</AlertTitle>{alert.message}
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