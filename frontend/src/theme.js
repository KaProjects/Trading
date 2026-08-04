import {createTheme} from "@mui/material";

export const appTheme = createTheme({
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
