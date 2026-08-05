import {Box, ButtonBase, ListSubheader, MenuItem, Select} from "@mui/material";
import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import {useEffect, useState} from "react";
import {recordEvent} from "../../service/utils";
import {getCompanyListKeys, getCompanyListTitle} from "../../service/CompanyListService";

const SUBTLE_DIVIDER = "rgba(0, 0, 0, 0.06)"
const CLEAR_VALUE = "__clear-selector__"

export const MainBarSelect = props => {
    const {value, setValue, label, valueKey, companyLists, defaultCompanyList = "all"} = props
    const companyPlaceholder = props.companyPlaceholder ?? "company"
    const listKeys = getCompanyListKeys(companyLists)
    const initialList = companyLists?.[props.companyListValue]
        ? props.companyListValue
        : companyLists?.[defaultCompanyList] ? defaultCompanyList : listKeys[0]
    const [activeList, setActiveList] = useState(initialList)
    const [selectingList, setSelectingList] = useState(false)
    const values = companyLists ? companyLists[activeList] ?? [] : props.values ?? []
    const selectedOption = getSelectedValue(companyLists?.all ?? values, value, valueKey)
    const selectedValue = companyLists ? selectedOption?.id ?? "" : selectedOption

    useEffect(() => {
        if (companyLists?.[props.companyListValue]) {
            setActiveList(props.companyListValue)
        }
    }, [companyLists, props.companyListValue])

    function openSelector() {
        if (companyLists) {
            setSelectingList(false)
        }
    }

    function selectCompanyList(listKey) {
        setActiveList(listKey)
        props.setCompanyListValue?.(listKey)
        setSelectingList(false)
        recordEvent(window.location.pathname + "#selector:company-list:" + listKey)
    }

    function changeValue(newValue) {
        const nextValue = newValue === CLEAR_VALUE
            ? ""
            : companyLists
                ? (companyLists.all ?? []).find(company => company.id === newValue) ?? ""
                : newValue

        if (companyLists && newValue !== CLEAR_VALUE) {
            props.setCompanyListValue?.(activeList)
        }
        setValue(nextValue)
        recordEvent(window.location.pathname + "#selector:" + label)
    }

    function renderSelectedCompany() {
        if (!selectedOption) {
            return []
        }

        return [
            <MenuItem key="selected-company" value={selectedOption.id} style={{display: "none"}}>
                {selectedOption[valueKey]}
            </MenuItem>
        ]
    }

    function renderClearOption() {
        return <MenuItem
            key="clear-selector"
            value={CLEAR_VALUE}
            sx={{
                justifyContent: "center",
                fontSize: "13px",
                borderBottom: `1px solid ${SUBTLE_DIVIDER}`,
            }}
        >
            clear
        </MenuItem>
    }

    function renderEmptyOption(placeholder) {
        return <MenuItem key="empty-selector" value="" style={{display: "none"}}>
            {placeholder}
        </MenuItem>
    }

    function renderCompanyListControl() {
        const buttonSx = {
            width: "100%",
            minWidth: "160px",
            justifyContent: "space-between",
            padding: "5px 14px",
            color: "text.secondary",
            fontSize: "0.95rem",
        }
        const listOptionSx = (listKey, isFirst) => ({
            width: "100%",
            minWidth: "160px",
            justifyContent: "flex-start",
            padding: "6px 16px",
            color: "text.primary",
            fontSize: "1rem",
            fontWeight: 400,
            borderTop: isFirst ? `1px solid ${SUBTLE_DIVIDER}` : "none",
            borderBottom: `1px solid ${SUBTLE_DIVIDER}`,
            "&:hover": {
                backgroundColor: "action.hover",
            },
        })
        const listHeader = (
            <ListSubheader key="company-list-control" disableSticky disableGutters>
                <ButtonBase
                    sx={buttonSx}
                    onClick={() => setSelectingList(current => !current)}
                    aria-label={`Company list ${getCompanyListTitle(activeList)}`}
                >
                    <Box component="span">{getCompanyListTitle(activeList)}</Box>
                    <ArrowDropDownIcon fontSize="small"/>
                </ButtonBase>
            </ListSubheader>
        )

        if (selectingList) {
            return [
                renderClearOption(),
                renderEmptyOption(companyPlaceholder),
                ...renderSelectedCompany(),
                listHeader,
                ...listKeys.map((listKey, index) => (
                    <ListSubheader
                        key={listKey}
                        disableSticky
                        disableGutters
                        sx={{lineHeight: "normal", backgroundColor: "background.paper"}}
                    >
                        <ButtonBase
                            sx={listOptionSx(listKey, index === 0)}
                            onClick={() => selectCompanyList(listKey)}
                            aria-label={`Use company list ${getCompanyListTitle(listKey)}`}
                            aria-current={listKey === activeList ? "true" : undefined}
                        >
                            {getCompanyListTitle(listKey)}
                        </ButtonBase>
                    </ListSubheader>
                )),
            ]
        }

        return [
            renderClearOption(),
            renderEmptyOption(companyPlaceholder),
            listHeader,
            ...(selectedOption && !values.some(option => option.id === selectedOption.id)
                ? renderSelectedCompany()
                : []),
            ...values.map((option, index) => renderCompanyOption(
                option,
                index,
                label,
                valueKey,
                index === 0
            )),
        ]
    }

    return (
        <Select
            labelId={props.labelId}
            required={props.required}
            fullWidth={props.fullWidth}
            error={props.error}
            value={selectedValue}
            renderValue={companyLists
                ? () => selectedOption?.[valueKey] ?? companyPlaceholder
                : undefined}
            variant="standard"
            sx={props.sx ?? {marginLeft: "15px", textAlign: "center", color: "white", '.MuiSvgIcon-root ': {fill: "white"},
                ':not(.Mui-disabled):hover::before': { borderBottomColor: '#1976d2' },
                ':before': { borderBottomColor: '#1976d2' },
                ':after': { borderBottomColor: '#1976d2' }}}
            onOpen={openSelector}
            onChange={event => changeValue(event.target.value)}
            displayEmpty
        >
            {companyLists
                ? renderCompanyListControl()
                : [
                    renderClearOption(),
                    renderEmptyOption(label),
                    ...values.map((option, index) => renderOption(
                        option,
                        index,
                        label,
                        valueKey,
                        index === 0
                    )),
                ]
            }
        </Select>
    )
}

function renderCompanyOption(option, index, label, valueKey, isFirst) {
    return (
        <MenuItem
            key={`${label}-${option[valueKey]}-${index}`}
            value={option.id}
            sx={{
                borderTop: isFirst ? `1px solid ${SUBTLE_DIVIDER}` : "none",
                borderBottom: `1px solid ${SUBTLE_DIVIDER}`,
                "&.Mui-selected": {
                    backgroundColor: "transparent",
                },
                "&.Mui-selected:hover": {
                    backgroundColor: "action.hover",
                },
            }}
        >
            {option[valueKey]}
        </MenuItem>
    )
}

function renderOption(option, index, label, valueKey, isFirst) {
    const optionLabel = valueKey ? option[valueKey] : option

    return (
        <MenuItem
            key={`${label}-${optionLabel}-${index}`}
            value={option}
            sx={{
                borderTop: isFirst ? `1px solid ${SUBTLE_DIVIDER}` : "none",
                borderBottom: `1px solid ${SUBTLE_DIVIDER}`,
                "&.Mui-selected": {
                    backgroundColor: "transparent",
                },
                "&.Mui-selected:hover": {
                    backgroundColor: "action.hover",
                },
            }}
        >
            {optionLabel}
        </MenuItem>
    )
}

function getSelectedValue(values, value, valueKey) {
    if (!value || !valueKey) {
        return value
    }

    return values.find(option => isSameOption(option, value, valueKey)) ?? ""
}

function isSameOption(option, value, valueKey) {
    if (option === value) {
        return true
    }
    if (option.id && value.id) {
        return option.id === value.id
    }
    if (option.key && value.key) {
        return option.key === value.key
    }
    return option[valueKey] === value[valueKey]
}
