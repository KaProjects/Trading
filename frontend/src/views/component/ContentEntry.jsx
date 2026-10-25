import {Box} from "@mui/material";
import React, {useRef, useState} from "react";
import {ContentEditor} from "./ContentEditor";
import {ContentImages} from "./ContentImages";
import {formatDate} from "../../service/FormattingService";

/** The editor's empty document: an entry holding only this, and no images, is not shown. */
const EMPTY_DOCUMENT = '[{"type":"paragraph","children":[{"text":""}]}]'

export function isEmptyEntry(entry) {
    const content = entry?.content
    const blank = !content || content.trim() === "" || content === EMPTY_DOCUMENT
    return blank && (entry?.images ?? []).length === 0
}

export const DateHeading = ({date, outside = false, testId}) => (
    <Box
        data-testid={testId}
        title={outside ? "This date is outside the period" : undefined}
        sx={{
            fontSize: 11,
            fontWeight: 600,
            textDecoration: "underline",
            alignSelf: "flex-start",
            color: outside ? "error.main" : "text.secondary",
        }}
    >
        {formatDate(date)}
    </Box>
);

/**
 * One day of a stream - a period's research, a case's entries - as text with its pictures
 * beside it. Everything that makes the writing work lives here: the editing controls follow the
 * cursor, Escape puts the text and the staged pictures back, and the file picker's own focus
 * loss does not count as leaving.
 */
export const ContentEntry = ({
    entry,
    outside = false,
    headed = true,
    spacingBefore = 0,
    compact,
    staged,
    onSave,
    onStageImage,
    onDropStagedImage,
    onMarkImageRemoved,
    onCommitImages,
    onDiscardImages,
    onOpenEditor,
    testIdFor = entry => `content-${entry.date}`,
    dateTestIdFor = entry => `content-date-${entry.date}`,
}) => {
    // Adding and removing pictures belongs to writing, so those controls appear with the cursor
    // and go again when it leaves. Focus is watched on the whole entry, which is what keeps them
    // in place while the strip beside the text is being used.
    const [editing, setEditing] = useState(false);
    // Escape puts the text back, and has to put the pictures back with it; anything else that
    // ends the writing keeps them, saving them alongside the text.
    const cancelled = useRef(false);

    function stopEditing() {
        setEditing(false);
        if (cancelled.current) {
            onDiscardImages(entry.id);
        } else {
            onCommitImages(entry.id);
        }
        cancelled.current = false;
    }

    return (
        <Box
            data-testid={testIdFor(entry)}
            // Only focus that lands inside the entry counts. The picture viewer is a child of
            // this entry in the React tree but a portal in the page, so without the check its
            // focus would read as the entry being written in, and merely opening a picture
            // would bring up the editing controls.
            onFocusCapture={event => {
                if (event.currentTarget.contains(event.target)) setEditing(true);
            }}
            onBlurCapture={event => {
                if (!event.currentTarget.contains(event.target)) return;
                if (event.currentTarget.contains(event.relatedTarget)) return;

                if (event.relatedTarget) {
                    stopEditing();
                    return;
                }

                // Focus went nowhere the page can name, which is both what clicking an empty
                // patch looks like and what the file picker looks like. The picker takes focus
                // off the window itself, so writing carries on until the window has it back and
                // the cursor is somewhere else.
                const entry = event.currentTarget;
                setTimeout(() => {
                    if (document.hasFocus() && !entry.contains(document.activeElement)) {
                        stopEditing();
                    }
                }, 150);
            }}
            sx={{display: "flex", flexDirection: "column", gap: "2px", marginTop: spacingBefore}}
        >
            {headed &&
                <DateHeading date={entry.date} outside={outside} testId={dateTestIdFor(entry)}/>
            }
            <Box sx={{
                display: "flex",
                flexDirection: compact ? "column" : "row",
                // The text and the pictures beside it are the same height, so the editor can be
                // reached by clicking anywhere in the entry rather than on the words themselves.
                alignItems: compact ? "flex-start" : "stretch",
                gap: compact ? "4px" : "8px",
            }}>
                <Box
                    sx={{
                        flex: "1 1 auto",
                        minWidth: 0,
                        width: "100%",
                        display: "flex",
                        flexDirection: "column",
                    }}
                    onClick={compact ? () => onOpenEditor(entry) : undefined}
                >
                    <ContentEditor
                        // The version makes a saved entry start the editor again on what was
                        // saved: without it the editor keeps showing the text it was opened with.
                        key={`${entry.id}-${entry.date}-${entry.version ?? 0}`}
                        content={entry.content}
                        update={value => onSave(entry.date, value)}
                        onCancel={() => {cancelled.current = true}}
                        style={{margin: "0 5px 3px 0", flex: "1 1 auto", minHeight: 0}}
                        locked={compact}
                    />
                </Box>
                <ContentImages
                    images={entry.images}
                    pending={staged.adds}
                    removedIds={staged.removes}
                    compact={compact}
                    editing={editing}
                    onAdd={file => onStageImage(entry.id, file)}
                    onDropPending={key => onDropStagedImage(entry.id, key)}
                    onMarkRemoved={(imageId, removed) =>
                        onMarkImageRemoved(entry.id, imageId, removed)}
                />
            </Box>
        </Box>
    );
};
