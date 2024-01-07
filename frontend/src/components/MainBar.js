import React from "react";
import {AppBar, Box, FormControlLabel, IconButton, Toolbar, Typography} from "@mui/material";
import MenuIcon from '@mui/icons-material/Menu';
import Checkbox from '@mui/material/Checkbox';


const MainBar = props => {


    return (
        <Box sx={{ flexGrow: 1 }}>
            <AppBar position="static">
                <Toolbar variant="dense">
                    <IconButton size="large" edge="start" color="inherit" aria-label="open drawer" sx={{ mr: 2 }}
                                onClick={event => {sessionStorage.removeItem('year');window.location.href='/'}}>
                        <MenuIcon />
                    </IconButton>
                    <Typography variant="h6" noWrap component="div" sx={{ display: { xs: 'none', sm: 'block' } }}>
                        Trading
                    </Typography>
                    <Box sx={{ flexGrow: 1 }} />
                    <Box sx={{ display: { xs: 'none', md: 'flex' } }}>
                        {props.showActiveSelector &&
                            <FormControlLabel control={<Checkbox checked={props.activeSelectorValue}
                                                                 onChange={props.toggleActiveSelectorValue}
                                                                 sx={{color: "white", '&.Mui-checked': {color: "white"}}}
                                                                 label="Only Active"/>}
                                              label={<Typography style={{fontFamily: "Roboto", fontSize: 13, marginRight: -4, paddingTop: 2}}>{"Only Active".toUpperCase()}</Typography>}
                                              labelPlacement="start"
                            />
                        }
                    </Box>
                    <Box sx={{ flexGrow: 1 }} />
                    <Box sx={{ display: { xs: 'none', md: 'flex' } }}>

                    </Box>
                </Toolbar>
            </AppBar>
        </Box>
    )
}
export default MainBar;