import {Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from "@mui/material";
import React from "react";


const Analytics = props => {
    return (
        <TableContainer component={Paper} sx={{ width: "max-content", margin: "10px auto 10px auto", maxHeight: "calc(100vh - 70px)"}}>
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
export default Analytics