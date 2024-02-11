import React from "react";
import {AppBar, Box, Button, IconButton, Tab, Tabs, Toolbar, Typography} from "@mui/material";
import MenuIcon from '@mui/icons-material/Menu';
import MainBarSelect from "./MainBarSelect";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline';


const MainBar = props => {

    return (
        <Box sx={{ flexGrow: 1 }}>
            <AppBar position="static">
                <Toolbar variant="dense">
                    <IconButton size="large" edge="start" color="inherit" aria-label="open drawer" sx={{ mr: 2 }}
                                onClick={() => window.location.href='/'}>
                        <MenuIcon />
                    </IconButton>
                    <Typography variant="h6" sx={{ display: { xs: 'none', sm: 'block' } }}>
                        Trading
                    </Typography>
                    <Box sx={{ flexGrow: 1 }} />
                    <Box sx={{ display: { xs: 'block', md: 'flex' } }}>
                        {props.showStatsTabs &&
                            <Tabs value={props.statsTabsIndex}
                                  onChange={(event, value) => props.setStatsTabsIndex(value)}
                                  TabIndicatorProps={{style: {backgroundColor: "white"}}}
                                  textColor="inherit"
                            >
                                <Tab label="Companies"/>
                                <Tab label="Monthly"/>
                                <Tab label="Yearly"/>
                            </Tabs>
                        }
                        {props.showSellTradeButton &&
                            <Button onClick={() => props.setOpenSellTrade(true)} sx={{}}>
                                <RemoveCircleOutlineIcon sx={{color: '#ff9f9f',}}/>
                            </Button>
                        }
                        {props.showAddTradeButton &&
                            <Button onClick={() => props.setOpenAddTrade(true)} sx={{marginRight: "25px"}}>
                                <ControlPointIcon sx={{color: 'lightgreen'}}/>
                            </Button>
                        }
                        {props.showAddDividendButton &&
                            <Button onClick={() => props.setOpenAddDividend(true)} sx={{marginRight: "25px"}}>
                                <ControlPointIcon sx={{color: 'lightgreen'}}/>
                            </Button>
                        }
                        {props.showActiveSelector !== null &&
                            <MainBarSelect values={props.showActiveSelector}
                                           value={props.activeSelectorValue}
                                           setValue={props.setActiveSelectorValue}
                                           label={"all"}
                            />
                        }
                        <Box sx={{ width: "15px" }} />
                        {props.showCompanySelector &&
                            <MainBarSelect values={props.companies}
                                           value={props.companySelectorValue}
                                           setValue={props.setCompanySelectorValue}
                                           label={"companies"}
                            />
                        }
                        <Box sx={{ width: "15px" }} />
                        {props.showCurrencySelector !== null &&
                            <MainBarSelect values={props.showCurrencySelector}
                                           value={props.currencySelectorValue}
                                           setValue={props.setCurrencySelectorValue}
                                           label={"currencies"}
                            />
                        }
                        <Box sx={{ width: "15px" }} />
                        {props.showYearSelector !== null &&
                            <MainBarSelect values={props.showYearSelector}
                                           value={props.yearSelectorValue}
                                           setValue={props.setYearSelectorValue}
                                           label={"years"}
                            />
                        }
                    </Box>
                    <Box sx={{ flexGrow: 1 }} />
                </Toolbar>
            </AppBar>
        </Box>
    )
}
export default MainBar;