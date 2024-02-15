import React from "react";
import SvgIcon from "@mui/material/SvgIcon";
import "../style/BorderedSection.css";

function BorderedSection({ icon, title, children }) {
    return (
        <div className={"mainContainer"}>
            <div className={"header"}>
                <div className={"headerBorderBefore"}></div>
                {(icon || title) && (
                    <div className={"headerTitle"}>
                        {icon && <SvgIcon component={icon} />}
                        {title && <span className={"title"}>{title}</span>}
                    </div>
                )}
                <div className={"headerBorderAfter"}></div>
            </div>
            <div className={"childrenContainer"}>{children}</div>
        </div>
    )
}
export default BorderedSection