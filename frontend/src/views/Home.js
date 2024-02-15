import {ButtonBase, Card, CardContent, Paper, Stack, Typography} from "@mui/material";
import React from "react";
import SettingsSuggestIcon from '@mui/icons-material/SettingsSuggest';
import {domain} from "../properties";
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import FormatListBulletedIcon from '@mui/icons-material/FormatListBulleted';
import CandlestickChartIcon from '@mui/icons-material/CandlestickChart';
import PercentIcon from '@mui/icons-material/Percent';
import StoreIcon from '@mui/icons-material/Store';
import DonutSmallIcon from '@mui/icons-material/DonutSmall';


const Home = props => {

    function ClickableCard(props) {
        const {action, title, icon, description} = props
        return(
            <ButtonBase onClick={action}>
                <Card sx={{ width: 300, height: 150 }} raised style={{backgroundColor: "#ffc107"}}>
                    <CardContent>
                        <Typography variant="h5" component="div" align={"center"}>
                            {title}
                        </Typography>
                        <Typography align={"center"}>
                            {icon}
                        </Typography>
                        <Typography variant="caption">
                            {description}
                        </Typography>
                    </CardContent>
                </Card>
            </ButtonBase>
        )
    }

    return (
        <Paper elevation={0} sx={{maxHeight: "calc(100vh - 70px)", overflowY: "scroll"}}>
            <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="center" alignItems={{ xs: 'middle', md: 'flex-start' }} spacing={2} sx={{marginTop: "20px"}}>
                <ClickableCard action={() => window.location.href='/trades'}
                               title={"Trades"}
                               icon={<CandlestickChartIcon/>}
                               description={"History of all trades (active, closed) with filters and their management."}
                />
                <ClickableCard action={() => window.location.href='/dividends'}
                               title={"Dividends"}
                               icon={<PercentIcon/>}
                               description={"History of all dividends received with filters and their management."}
                />
            </Stack>

            <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="center"alignItems={{ xs: 'middle', md: 'flex-start' }} spacing={2} sx={{marginTop: "20px"}}>
                <ClickableCard action={() => window.location.href='/records'}
                               title={"Records"}
                               icon={<FormatListBulletedIcon/>}
                               description={"Collection of records for companies including related data like financials and ratios"}
                />
                <ClickableCard action={() => window.location.href='/companies'}
                               title={"Companies"}
                               icon={<StoreIcon/>}
                               description={"List of companies with their attributes, aggregated data and their management"}
                />
            </Stack>

            <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="center" alignItems={{ xs: 'middle', md: 'flex-start' }} spacing={2} sx={{marginTop: "20px", marginBottom: "20px"}}>
                <ClickableCard action={() => window.location.href='/stats'}
                               title={"Stats"}
                               icon={<QueryStatsIcon/>}
                               description={"A collection, description, analysis, and inference of conclusions from quantitative data."}
                />
                <ClickableCard action={() => window.open(domain + '/api/docs', '_blank')}
                               title={"API"}
                               icon={<SettingsSuggestIcon/>}
                               description={"A back-end API that helps to interact with back-end services."}
                />
            </Stack>

            <Typography style={{width: '100%', position: 'fixed', bottom: 0}} component="footer" align={"center"}>
                Copyright © {new Date().getFullYear()} Stanislav Kaleta
            </Typography>
            <DonutSmallIcon style={{position: "fixed", bottom: 0, right: 0}} onClick={() => window.location.href='/analytics'}/>
        </Paper>
    )
}
export default Home