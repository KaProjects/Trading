import {formatDate, formatError} from "../../service/FormattingService";
import {Box, Button, Dialog, DialogActions, DialogTitle, Stack, Tooltip} from "@mui/material";
import {AssetBox} from "./AssetBox";
import React, {useEffect, useState} from "react";
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


export const Record = ({record, currency, setAlert, deleteRecord}) => {

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
            .then(response => {})
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

    return (
        <BorderedSection title={formatDate(record.date)} style={{color: 'text.primary'}}>

            <Stack direction="row" justifyContent="flex-start" alignItems="stretch" spacing={2}>
                <Box>{currency}{record.price}</Box>
                <Box>PS:{record.priceToRevenues}</Box>
                <Box>PG:{record.priceToGrossProfit}</Box>
                <Box>PO:{record.priceToOperatingIncome}</Box>
                <Box>PE:{record.priceToNetIncome}</Box>
                <Box>DY:{record.dividendYield}</Box>
                <EditableValueBox
                    value={record.targets}
                    suffix={currency}
                    label={"Targets"}
                    style={{marginTop: "-4px"}}
                    validate={() => ""}
                    update={(value) => updateTargets(value)}
                />
            </Stack>

            {record.asset &&
                <Stack direction="row" justifyContent="flex-start" alignItems="stretch" spacing={2}>
                    <AssetBox asset={record.asset} currency={currency}/>
                </Stack>
            }
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
