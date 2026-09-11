import React from "react";
import {ButtonBase, Card, CardContent, Paper, Stack, Typography} from "@mui/material";
import {backend} from "../properties";

const ADMIN_PAGES = [
    {title: "Companies", action: () => window.location.href = "/admin/companies"},
    {title: "Trade Import", action: () => window.location.href = "/admin/import/trades"},
    {title: "Dividend Import", action: () => window.location.href = "/admin/import/dividends"},
    {title: "API Docs", action: () => window.open(backend + "/api/docs/", "_blank")},
];

export const Admin = () => {

    function AdminCard({title, action}) {
        return (
            <ButtonBase onClick={action} sx={{width: "100%"}}>
                <Card sx={{width: "100%"}} raised style={{backgroundColor: "#ffc107"}}>
                    <CardContent sx={{padding: "12px", "&:last-child": {paddingBottom: "12px"}}}>
                        <Typography variant="h6" component="div" align="center">
                            {title}
                        </Typography>
                    </CardContent>
                </Card>
            </ButtonBase>
        )
    }

    return (
        <Paper elevation={0}>
            <Stack
                direction="column"
                spacing={2}
                sx={{width: "100%", maxWidth: 300, margin: "20px auto"}}
            >
                {ADMIN_PAGES.map(page => (
                    <AdminCard key={page.title} title={page.title} action={page.action}/>
                ))}
            </Stack>
        </Paper>
    )
}
