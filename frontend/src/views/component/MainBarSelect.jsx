import {MenuItem, Select} from "@mui/material";
import {recordEvent} from "../../service/utils";


export const MainBarSelect = props => {
    const {values, value, setValue, label, valueKey} = props
    const selectedValue = getSelectedValue(values, value, valueKey)

    return (
        <Select
            value={selectedValue}
            variant="standard"
            sx={{marginLeft: "15px", textAlign: "center", color: "white", '.MuiSvgIcon-root ': {fill: "white"},
                ':not(.Mui-disabled):hover::before': { borderBottomColor: '#1976d2' },
                ':before': { borderBottomColor: '#1976d2' },
                ':after': { borderBottomColor: '#1976d2' }}}
            onChange={event => {setValue(event.target.value);recordEvent(window.location.pathname + "#selector:" + label);}}
            displayEmpty
        >
            <MenuItem value="">{label}</MenuItem>
            {values.map((option, index) => {
                const optionLabel = valueKey ? option[valueKey] : option

                return (
                    <MenuItem key={`${label}-${optionLabel}-${index}`} value={option}>
                        {optionLabel}
                    </MenuItem>
                )
            })}
        </Select>
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
