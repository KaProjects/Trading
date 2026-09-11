import React from "react";
import {
    Box,
    Divider,
    List,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    Paper,
    Typography,
} from "@mui/material";
import StoreIcon from "@mui/icons-material/Store";
import CallSplitIcon from "@mui/icons-material/CallSplit";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import ApiIcon from "@mui/icons-material/Api";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import {apiDocsUrl} from "../properties";

const ADMIN_GROUPS = [
    {
        title: "Data",
        pages: [
            {title: "Companies", icon: StoreIcon, path: "/admin/companies"},
            {title: "Stock Split", icon: CallSplitIcon, path: "/admin/split"},
        ],
    },
    {
        title: "Import",
        pages: [
            {title: "Trade Import", icon: UploadFileIcon, path: "/admin/import/trades"},
            {title: "Dividend Import", icon: UploadFileIcon, path: "/admin/import/dividends"},
        ],
    },
    {
        title: "Reference",
        pages: [
            {title: "API Docs", icon: ApiIcon, external: true},
        ],
    },
];

export const Admin = () => {

    function open(page) {
        if (page.external) {
            window.open(apiDocsUrl, "_blank")
        } else {
            window.location.href = page.path
        }
    }

    return (
        <Box sx={{
            width: {xs: "calc(100% + 16px)", sm: "100%"},
            maxWidth: {sm: 420},
            margin: {xs: "0 -8px", sm: "24px auto"},
            padding: {xs: 0, sm: "0 8px"},
        }}>
            <Paper variant="outlined" sx={{borderRadius: {xs: 0, sm: 1}, borderWidth: {xs: "1px 0", sm: "1px"}}}>
                {ADMIN_GROUPS.map((group, groupIndex) => (
                    <Box key={group.title}>
                        {groupIndex > 0 && <Divider/>}
                        <Typography
                            sx={{
                                padding: "10px 16px 4px",
                                fontSize: 11,
                                fontWeight: 700,
                                letterSpacing: "0.08em",
                                textTransform: "uppercase",
                                color: "text.secondary",
                            }}
                        >
                            {group.title}
                        </Typography>
                        <List disablePadding>
                            {group.pages.map(page => (
                                <ListItemButton key={page.title} onClick={() => open(page)}>
                                    <ListItemIcon sx={{minWidth: 38, color: "text.secondary"}}>
                                        <page.icon fontSize="small"/>
                                    </ListItemIcon>
                                    <ListItemText
                                        primary={page.title}
                                        slotProps={{primary: {fontSize: 15}}}
                                    />
                                    {page.external
                                        ? <OpenInNewIcon fontSize="small" sx={{color: "text.disabled"}}/>
                                        : <ChevronRightIcon fontSize="small" sx={{color: "text.disabled"}}/>}
                                </ListItemButton>
                            ))}
                        </List>
                    </Box>
                ))}
            </Paper>
        </Box>
    )
}
