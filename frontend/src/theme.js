import {createTheme} from "@mui/material";

export const appTheme = createTheme({
    breakpoints: {
        values: {
            xs: 0,
            sm: 700,
            md: 900,
            lg: 1200,
            xl: 1536,
        },
    },
    components: {
        MuiDialog: {
            styleOverrides: {
                paper: ({theme}) => ({
                    [theme.breakpoints.down("sm")]: {
                        margin: 0,
                        width: "100%",
                        maxWidth: "100%",
                        height: "100%",
                        maxHeight: "none",
                        borderRadius: 0,
                    },
                }),
            },
        },
    },
});
