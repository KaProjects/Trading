import {formatDate, formatDecimals, formatError} from "../../service/FormattingService";
import {Box, Button, Dialog, DialogActions, DialogTitle, Stack, Tooltip} from "@mui/material";
import {RecordAssetAggregate} from "./RecordAssetAggregate";
import React, {useState} from "react";
import axios from "axios";
import {backend} from "../../properties";
import {EditableTypography} from "./EditableTypography";
import {BorderedSection} from "./BorderedSection";
import {defaultContent} from "./ContentEditor";
import {RecordEditorSection} from "./RecordEditorSection";
import {ReactComponent as ContentPlusIcon} from "../../assets/icons/content-plus.svg";
import {ReactComponent as ReviewPlusIcon} from "../../assets/icons/review-plus.svg";
import {ReactComponent as StrategyPlusIcon} from "../../assets/icons/strategy-plus.svg";
import {ReactComponent as RetroPlusIcon} from "../../assets/icons/retro-plus.svg";
import {ReactComponent as DeleteIcon} from "../../assets/icons/delete.svg";
import {EditableValueBox} from "./EditableValueBox";
import {validateNumber} from "../../service/ValidationService";

const FINANCIAL_RATIO_LABELS = ["PS", "PG", "PO", "PE"];

function financialRatiosValue(record) {
    return [
        record.priceToRevenues,
        record.priceToGrossProfit,
        record.priceToOperatingIncome,
        record.priceToNetIncome,
    ].map(value => value ?? "").join("/");
}

function hasValue(value) {
    return value !== null && value !== undefined && value !== "";
}

function hasFinancialRatios(record) {
    return [
        record.priceToRevenues,
        record.priceToGrossProfit,
        record.priceToOperatingIncome,
        record.priceToNetIncome,
    ].some(hasValue);
}

function hasAssetAggregate(asset) {
    return asset && hasValue(asset.quantity) && hasValue(asset.purchasePrice);
}

function formatFinancialRatios(value) {
    return value.split("/")
        .map(ratio => ratio.trim())
        .map(ratio => ratio === "" ? "-" : formatDecimals(Number(ratio), 0, 2))
        .join(" / ");
}

function validateFinancialRatios(value) {
    const ratios = value.split("/").map(ratio => ratio.trim());
    if (ratios.length !== 4) return "Use format PS/PG/PO/PE";

    for (let index = 0; index < ratios.length; index++) {
        const error = validateNumber(ratios[index], false, 6, 2, index > 0);
        if (error) return `${FINANCIAL_RATIO_LABELS[index]}: ${error}`;
    }
    return "";
}


export const Record = ({data, currency, setAlert, deleteRecord}) => {

    const [record, setRecord] = useState(data);

    const [reviewSectionAdded, setReviewSectionAdded] = useState(false);
    const [strategySectionAdded, setStrategySectionAdded] = useState(false);
    const [retroSectionAdded, setRetroSectionAdded] = useState(false);
    const [contentSectionAdded, setContentSectionAdded] = useState(false);
    const [openDeleteDialog, setOpenDeleteDialog] = useState(false);

    function updateTitle(id, value) {
        return axios.put(backend + "/record", {id: id, title: value})
            .then(response => {})
            .catch((error) => {
                const formatted = formatError(error)
                setAlert(formatted)
                return formatted
            })
    }

    function updateRecord(data) {
        return axios.put(backend + "/record", data)
            .then(response => {
                setRecord(prev => ({...prev, ...data}));
            })
            .catch((error) => {
                const formatted = formatError(error)
                setAlert(formatted)
                return formatted
            })
    }

    function updateContent(value) {
        updateRecord({id: record.id, content: JSON.stringify(value)})
    }

    function updateReview(value) {
        updateRecord({id: record.id, review: JSON.stringify(value)})
    }

    function updateStrategy(value) {
        updateRecord({id: record.id, strategy: JSON.stringify(value)})
    }

    function updateRetro(value) {
        updateRecord({id: record.id, retro: JSON.stringify(value)})
    }

    function updateTargets(value) {
        updateRecord({id: record.id, targets: value})
    }

    function updatePrice(value) {
        return updateRecord({id: record.id, price: value})
    }

    function updateFinancialRatios(value) {
        const [priceToRevenues, priceToGrossProfit, priceToOperatingIncome, priceToNetIncome]
            = value.split("/").map(ratio => ratio.trim());
        return updateRecord({
            id: record.id,
            priceToRevenues,
            priceToGrossProfit,
            priceToOperatingIncome,
            priceToNetIncome,
        })
    }

    function updateAsset(quantity, purchasePrice) {
        return updateRecord({
            id: record.id,
            sumAssetQuantity: quantity,
            avgAssetPrice: purchasePrice,
        })
    }

    function showReviewSection() {
        return (record.review && record.review !== JSON.stringify(defaultContent())) || reviewSectionAdded
    }

    function showStrategySection() {
        return (record.strategy && record.strategy !== JSON.stringify(defaultContent())) || strategySectionAdded
    }

    function showRetroSection() {
        return (record.retro && record.retro !== JSON.stringify(defaultContent())) || retroSectionAdded
    }

    function showContentSection() {
        return (record.content && record.content !== JSON.stringify(defaultContent())) || contentSectionAdded
    }

    const showAssetAggregate = hasAssetAggregate(record.asset);
    const showFinancialRatios = hasFinancialRatios(record);
    const showDividendYield = hasValue(record.dividendYield);
    const summaryColumns = [
        showAssetAggregate && "asset",
        "price",
        showFinancialRatios && "ratios",
        showDividendYield && "dividend",
    ].filter(Boolean);
    const summarySecondRow = summaryColumns
        .map(column => column === "asset" ? "asset" : column === "price" ? "targets" : ".")
        .join(" ");

    return (
        <BorderedSection title={formatDate(record.date)} style={{color: 'text.primary'}}>

            <Box
                data-testid="record-summary-scroll"
                sx={{
                    maxWidth: "100%",
                    overflowX: {xs: "auto", sm: "visible"},
                    overflowY: {xs: "hidden", sm: "visible"},
                    overscrollBehaviorX: "contain",
                    pb: {xs: 0.5, sm: 0},
                }}
            >
                <Box
                    data-testid="record-summary"
                    sx={{
                        display: "grid",
                        gridTemplateAreas: `"${summaryColumns.join(" ")}" "${summarySecondRow}"`,
                        gridTemplateColumns: `repeat(${summaryColumns.length}, max-content)`,
                        gridTemplateRows: "max-content max-content",
                        columnGap: "5px",
                        rowGap: "1px",
                        alignItems: "start",
                        width: "max-content",
                    }}
                >
                    {showAssetAggregate &&
                        <Box data-testid="record-asset-item" sx={{gridArea: "asset"}}>
                            <RecordAssetAggregate
                                asset={record.asset}
                                currency={currency}
                                update={(quantity, purchasePrice) => updateAsset(quantity, purchasePrice)}
                            />
                        </Box>
                    }
                    <Box data-testid="record-price-item" sx={{gridArea: "price"}}>
                        <EditableValueBox
                            value={record.price}
                            prefix={currency}
                            label="Price"
                            formatValue={(value) => formatDecimals(Number(value), 0, 4)}
                            validate={(value) => validateNumber(value, false, 10, 4, false)}
                            update={updatePrice}
                            disabled
                        />
                    </Box>
                    <Box
                        data-testid="record-targets-item"
                        sx={{gridArea: "targets", width: 0, minWidth: 0, overflow: "visible"}}
                    >
                        <Box sx={{width: "max-content"}}>
                            <EditableValueBox
                                value={record.targets}
                                suffix={currency}
                                label={"Targets"}
                                validate={() => ""}
                                update={(value) => updateTargets(value)}
                            />
                        </Box>
                    </Box>
                    {showFinancialRatios &&
                        <Box data-testid="record-ratios-item" sx={{gridArea: "ratios"}}>
                            <EditableValueBox
                                value={financialRatiosValue(record)}
                                label="Price to financials ratios"
                                formatValue={formatFinancialRatios}
                                validate={validateFinancialRatios}
                                update={updateFinancialRatios}
                                disabled
                            />
                        </Box>
                    }
                    {showDividendYield &&
                        <Box data-testid="record-dividend-item" sx={{gridArea: "dividend"}}>
                            <EditableValueBox
                                value={record.dividendYield}
                                suffix="%"
                                label="Dividend yield"
                                formatValue={(value) => formatDecimals(Number(value), 0, 2)}
                                disabled
                            />
                        </Box>
                    }
                </Box>
            </Box>
            {record.title &&
                <EditableTypography
                    value={record.title}
                    label={"Title"}
                    update={(value) => updateTitle(record.id, value)}
                    validate={() => ""}
                    style={{margin: "12px 15px 0 5px"}}
                />
            }
            {showReviewSection() &&
                <RecordEditorSection
                    label={"Review"}
                    content={record.review}
                    update={(value) => updateReview(value)}
                    style={{marginTop: {xs: "5px", sm: "8px"}}}
                />
            }
            {showStrategySection() &&
                <RecordEditorSection
                    label={"Strategy"}
                    content={record.strategy}
                    update={(value) => updateStrategy(value)}
                />
            }
            {showRetroSection() &&
                <RecordEditorSection
                    label={"Retrospective"}
                    content={record.retro}
                    update={(value) => updateRetro(value)}
                />
            }
            {showContentSection() &&
                <RecordEditorSection
                    label={"Content"}
                    content={record.content}
                    update={(value) => updateContent(value)}
                />
            }

            <Stack direction="column" justifyContent="flex-start" alignItems="center" spacing={1}
                   sx={{
                       position: "absolute", top: "6px", right: "8px", zIndex: 1, opacity: 0, pointerEvents: "none",
                       maxHeight: "calc(100% - 12px)", overflowY: "auto", overflowX: "hidden",
                       paddingRight: "8px", marginRight: "-8px",
                       transition: "opacity 120ms ease-in-out",
                       ".mainContainer:hover &": {opacity: 1, pointerEvents: "auto",},
                       "& .MuiButton-root": {minWidth: 0, padding: "2px", lineHeight: 0,},
                       "& svg": {width: "20px", height: "20px", display: "block",},
                   }}
            >
                {!showReviewSection() &&
                    <Tooltip title="Add review section" placement="left">
                        <Button onClick={() => setReviewSectionAdded(true)}>
                            <ReviewPlusIcon/>
                        </Button>
                    </Tooltip>
                }
                {!showStrategySection() &&
                    <Tooltip title="Add strategy section" placement="left">
                        <Button onClick={() => setStrategySectionAdded(true)}>
                            <StrategyPlusIcon/>
                        </Button>
                    </Tooltip>
                }
                {!showRetroSection() &&
                    <Tooltip title="Add retro section" placement="left">
                        <Button onClick={() => setRetroSectionAdded(true)}>
                            <RetroPlusIcon/>
                        </Button>
                    </Tooltip>
                }
                {!showContentSection() &&
                    <Tooltip title="Add content section" placement="left">
                        <Button onClick={() => setContentSectionAdded(true)}>
                            <ContentPlusIcon/>
                        </Button>
                    </Tooltip>
                }
                <Tooltip title="Delete record" placement="left">
                    <Button onClick={() => setOpenDeleteDialog(true)}>
                        <DeleteIcon/>
                    </Button>
                </Tooltip>
            </Stack>
            <Dialog open={openDeleteDialog} onClose={() => setOpenDeleteDialog(false)}>
                <DialogTitle>Delete record?</DialogTitle>
                <DialogActions>
                    <Button onClick={() => setOpenDeleteDialog(false)}>Cancel</Button>
                    <Button
                        color="error"
                        onClick={() => {setOpenDeleteDialog(false);deleteRecord(record.id);}}
                        autoFocus
                    >
                        Delete
                    </Button>
                </DialogActions>
            </Dialog>
        </BorderedSection>
    )
}
