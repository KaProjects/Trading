import {MenuItem, Select} from "@mui/material";


const MainBarSelect = props => {
    const {values, value, setValue, label} = props
    return (
        <Select
            value={value}
            variant="standard"
            sx={{marginLeft: "15px", textAlign: "center", color: "white", '.MuiSvgIcon-root ': {fill: "white"},
                ':not(.Mui-disabled):hover::before': { borderBottomColor: '#1976d2' },
                ':before': { borderBottomColor: '#1976d2' },
                ':after': { borderBottomColor: '#1976d2' }}}
            onChange={event => setValue(event.target.value)}
            displayEmpty
        >
            <MenuItem value="">{label}</MenuItem>
            {values.map((value, index) => (
                <MenuItem key={index} value={value} >{(value.ticker === undefined) ? value : value.ticker}</MenuItem>
            ))}
        </Select>
    )
}
export default MainBarSelect