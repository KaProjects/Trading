import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import React from "react";


export const Analytics = props => {
    return (
        <TableContainer component={Paper} sx={{width: {xs: "100%", sm: "max-content"}, margin: "10px auto 10px auto", maxHeight: "calc(100vh - var(--main-bar-height, 48px) - 32px)", overflow: "auto"}}>
            <Table size="small" aria-label="a dense table" stickyHeader>
                <TableHead>
                    <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Used</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {Object.entries({...localStorage}).sort().map((record, index) => (
                        <TableRow key={index}>
                            <TableCell>{record[0]}</TableCell>
                            <TableCell>{record[1]}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </TableContainer>
    )
}
