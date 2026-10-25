import {Box} from "@mui/material";
import {ContentEditor} from "./ContentEditor";

export const RecordEditorSection = ({label, content, update, style, onOpen}) => (
    <Box data-testid={`record-section-${label.toLowerCase()}`} sx={{margin: "0 15px 10px 5px", ...style}}>
        <Box sx={{color: "text.secondary", fontSize: 14, fontWeight: 600, textDecoration: "underline", marginLeft: "5px"}}>
            {label}:
        </Box>
        <Box onClick={onOpen} sx={onOpen ? {cursor: "pointer"} : undefined}>
            <ContentEditor
                content={content}
                update={update}
                locked={Boolean(onOpen)}
                style={{margin: "2px 5px 0 5px"}}
            />
        </Box>
    </Box>
)
