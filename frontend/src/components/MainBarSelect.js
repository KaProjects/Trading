import {MenuItem, Select} from "@mui/material";
import {recordEvent} from "../service/utils";


const MainBarSelect = props => {
    const {values, value, setValue, label, valueKey} = props
    return (
        <Select
            value={value}
            variant="standard"
            sx={{marginLeft: "15px", textAlign: "center", color: "white", '.MuiSvgIcon-root ': {fill: "white"},
                ':not(.Mui-disabled):hover::before': { borderBottomColor: '#1976d2' },
                ':before': { borderBottomColor: '#1976d2' },
                ':after': { borderBottomColor: '#1976d2' }}}
            onChange={event => {setValue(event.target.value);recordEvent(window.location.pathname + "#selector:" + label);}}
            displayEmpty
        >
            <MenuItem value="">{label}</MenuItem>
            {values.map((value, index) => (
                <MenuItem key={index} value={value} >{(valueKey) ? value[valueKey] : value}</MenuItem>
            ))}
        </Select>
    )
}
export default MainBarSelect