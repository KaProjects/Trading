import React from "react";
import SvgIcon from "@mui/material/SvgIcon";
import "../../style/BorderedSection.css";

export function BorderedSection({ icon, title, titleEnd, children, style, stretch, highlightTitle }) {
    return (
        <div className={"mainContainer" + (stretch ? " stretch" : "")} style={style}>
            <div className={"header"}>
                <div className={"headerBorderBefore"}></div>
                {(icon || title) && (
                    <div className={"headerTitle" + (highlightTitle ? " highlighted" : "")}>
                        {icon && <SvgIcon component={icon} />}
                        {title && <span className={"title"}>{title}</span>}
                    </div>
                )}
                <div className={"headerBorderAfter" + (titleEnd ? " withEnd" : "")}></div>
                {titleEnd && (
                    <>
                        <span className={"headerTitleEnd"}>{titleEnd}</span>
                        <div className={"headerBorderEnd"}></div>
                    </>
                )}
            </div>
            <div className={"childrenContainer" + (stretch ? " stretchContent" : "")}>{children}</div>
        </div>
    )
}
