import {Box, IconButton, Tooltip} from "@mui/material";
import React from "react";

export const MainBarIconButton = ({tooltip, ariaLabel, onClick, href, icon, image, alt, color, buttonSx, iconSx}) => (
    <Tooltip title={tooltip}>
        <IconButton
            component={href ? "a" : "button"}
            href={href}
            target={href ? "_blank" : undefined}
            rel={href ? "noopener noreferrer" : undefined}
            onClick={onClick}
            aria-label={ariaLabel}
            size="small"
            sx={buttonSx}
        >
            <Box
                component={image ? "img" : icon}
                src={image}
                alt={alt}
                sx={image ? iconSx : {color, ...iconSx}}
            />
        </IconButton>
    </Tooltip>
)
