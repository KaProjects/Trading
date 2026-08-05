import React from "react";
import {FormControl, FormHelperText, InputLabel} from "@mui/material";
import {MainBarSelect} from "../../views/component/MainBarSelect";

export const DialogCompanySelect = ({
    companyLists,
    defaultCompanyList,
    value,
    onChange,
    id,
    sx,
}) => {
    const labelId = `${id}-label`;
    const invalid = !value;

    return (
        <FormControl required error={invalid} fullWidth variant="standard" sx={{marginTop: "20px", ...sx}}>
            <InputLabel id={labelId}>Company</InputLabel>
            <MainBarSelect
                companyLists={companyLists}
                defaultCompanyList={defaultCompanyList}
                value={value}
                setValue={onChange}
                valueKey="ticker"
                label="companies"
                companyPlaceholder=""
                labelId={labelId}
                required
                error={invalid}
                fullWidth
                sx={{
                    marginLeft: 0,
                    textAlign: "left",
                    color: "text.primary",
                    ".MuiSvgIcon-root": {fill: "text.secondary"},
                }}
            />
            <FormHelperText>{invalid ? "not filled" : ""}</FormHelperText>
        </FormControl>
    );
};
