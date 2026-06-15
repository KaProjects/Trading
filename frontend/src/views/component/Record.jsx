import {formatDate, formatError} from "../../service/FormattingService";
import {Box, Stack} from "@mui/material";
import {AssetBox} from "./AssetBox";
import React from "react";
import axios from "axios";
import {backend} from "../../properties";
import {EditableTypography} from "./EditableTypography";
import {BorderedSection} from "./BorderedSection";
import {ContentEditor} from "./ContentEditor";

export const Record = ({record, currency, setAlert}) => {

    function updateTitle(id, value) {
        return axios.put(backend + "/record", {id: id, title: value})
            .then(response => {})
            .catch((error) => {
                const formatted = formatError(error)
                setAlert(formatted)
                return formatted
            })
    }

    function updateContent(id, content) {
        return axios.put(backend + "/record", {id: id, content: JSON.stringify(content)})
            .then(response => {})
            .catch((error) => {
                const formatted = formatError(error)
                setAlert(formatted)
                return formatted
            })
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
                <Box>t:{record.targets}</Box>
                <Box>s:{record.strategy}</Box>
            </Stack>

            {record.asset &&
                <Stack direction="row" justifyContent="flex-start" alignItems="stretch" spacing={2}>
                    <AssetBox asset={record.asset} currency={currency}/>
                </Stack>
            }

            <EditableTypography
                value={record.title}
                label={"Title"}
                update={(value) => updateTitle(record.id, value)}
                validate={(value) => {if (value === "") return "not null"; return ""}}
                style={{margin: "12px 15px 0 5px"}}
            />

            <ContentEditor
                content={record.content}
                update={(value) => updateContent(record.id, value)}
                style={{margin: "15px 0 0 0"}}
            />

        </BorderedSection>
    )
}