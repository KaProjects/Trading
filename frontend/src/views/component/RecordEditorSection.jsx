import {Box} from "@mui/material";
import {ContentEditor} from "./ContentEditor";

export const RecordEditorSection = ({label, content, update, style}) => (
    <Box sx={{margin: "0 15px 10px 5px", ...style}}>
        <Box sx={{color: "text.secondary", fontSize: 14, fontWeight: 600, textDecoration: "underline", marginLeft: "5px"}}>
            {label}:
        </Box>
        <Box sx={{}}>
            <ContentEditor
                content={content}
                update={update}
                style={{margin: "2px 5px 0 5px"}}
            />
        </Box>
    </Box>
)
