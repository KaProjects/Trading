import {Box} from "@mui/material";
import React from "react";


const LatestValueBox = props => {
    const {label, data, suffix, sx} = props

    return (
        <React.Fragment>
            {data &&
                <Box sx={sx}>
                    <Box sx={{color: 'text.primary', fontSize: 16, textAlign: "center", fontFamily: "Roboto",}}>
                        {data.value + suffix}
                    </Box>
                    <Box sx={{color: 'lightgrey', fontWeight: 'bold', mx: 0.5, fontSize: 12, textAlign: "center"}}>
                        {label}
                    </Box>
                    <Box sx={{color: 'lightgrey', mx: 0.5, fontSize: 10, textAlign: "center"}}>
                        {data.date}
                    </Box>
                </Box>
            }
        </React.Fragment>

    )
}
export default LatestValueBox