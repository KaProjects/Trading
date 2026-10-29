import {Box, CircularProgress, Dialog, IconButton} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import CloseIcon from "@mui/icons-material/Close";
import RestoreIcon from "@mui/icons-material/Restore";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import KeyboardArrowLeftIcon from "@mui/icons-material/KeyboardArrowLeft";
import KeyboardArrowRightIcon from "@mui/icons-material/KeyboardArrowRight";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import useMediaQuery from "@mui/material/useMediaQuery";
import React, {useCallback, useEffect, useRef, useState} from "react";
import {ReactComponent as DeleteIcon} from "../../assets/icons/delete.svg";
import {backend} from "../../properties";
import {SWIPE_NAV_BREAKPOINT} from "./MainBar";
import {SWIPE_THRESHOLD} from "./useSwipeNavigation";

const THUMBNAIL = 76;
/** Big enough to hit on a thumbnail, small enough to leave the picture readable behind it. */
const OVERLAY_ICON = 21;

const VIEWER_PAPER = {
    margin: "24px",
    width: "auto",
    height: "auto",
    maxWidth: "calc(100% - 48px)",
    maxHeight: "calc(100% - 48px)",
    borderRadius: "4px",
    overflow: "hidden",
};

export const imageUrl = (imageId, thumbnail = false) =>
    `${backend}/period/content/image/${imageId}${thumbnail ? "?thumbnail=true" : ""}`;

export const caseImageUrl = (imageId, thumbnail = false) =>
    `${backend}/case/entry/image/${imageId}${thumbnail ? "?thumbnail=true" : ""}`;

/**
 * The pictures of one day's research: a strip of thumbnails beside the text on a wide screen and
 * under it on a narrow one, each opening full size, with a tile at its head to add another while
 * the entry is being written.
 *
 * Nothing is written while the entry is: a picture added waits as a preview, and one crossed out
 * stays where it is, greyed, until the entry is saved. Both go back the way they were if the
 * writing is cancelled.
 *
 * The strip never grows past the text it belongs to - it scrolls instead - so an arrow marks
 * either end that still has something beyond it.
 */
export const ContentImages = ({
    images = [],
    pending = [],
    removedIds = [],
    compact = false,
    editing = false,
    urlOf = imageUrl,
    onAdd,
    onDropPending,
    onMarkRemoved,
}) => {
    // How the strip is laid out and how the opened picture is stepped through are two
    // questions: a strip lies in a row under the text wherever a case is read, while the
    // arrows belong on a screen wide enough to have room for them beside the picture.
    const narrow = useMediaQuery(`(max-width:${SWIPE_NAV_BREAKPOINT}px)`);
    const [openedIndex, setOpenedIndex] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [dragging, setDragging] = useState(false);
    const [reach, setReach] = useState({before: false, after: false});
    const fileInput = useRef(null);
    const strip = useRef(null);
    const viewerSwipe = useRef(null);

    // There is as much to say about what has been scrolled past as about what is still to come,
    // so both ends are marked while there is something that way.
    const updateScrollHint = useCallback(() => {
        const box = strip.current;
        if (!box) return setReach({before: false, after: false});

        setReach(compact
            ? {
                before: box.scrollLeft > 1,
                after: box.scrollLeft + box.clientWidth < box.scrollWidth - 1,
            }
            : {
                before: box.scrollTop > 1,
                after: box.scrollTop + box.clientHeight < box.scrollHeight - 1,
            });
    }, [compact]);

    useEffect(() => {
        // Writing starts at the head of the strip, where the tile that adds a picture is; a
        // strip left halfway down would otherwise hide it. The narrow layout shows that tile in
        // the editing dialog instead, so only the column beside the text needs this.
        if (editing && !compact && strip.current) strip.current.scrollTop = 0;
    }, [editing, compact]);

    // A picture is stepped through with the arrow keys where there is a keyboard, and with a
    // swipe where there is not. The listener is on the document because the dialog's own focus
    // sits wherever it landed when it opened.
    useEffect(() => {
        if (openedIndex === null) return;

        const onKeyDown = event => {
            if (event.key === "ArrowLeft") step(-1);
            if (event.key === "ArrowRight") step(1);
        };
        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
        // eslint-disable-next-line
    }, [openedIndex, images.length]);

    useEffect(() => {
        updateScrollHint();
        if (typeof ResizeObserver === "undefined") return;

        const box = strip.current;
        if (!box) return;

        const observer = new ResizeObserver(updateScrollHint);
        observer.observe(box);
        return () => observer.disconnect();
    }, [images.length, pending.length, editing, updateScrollHint]);

    if (images.length === 0 && pending.length === 0 && !editing) return null;

    async function upload(files) {
        const file = (files ?? [])[0];
        if (!file || uploading) return;

        setUploading(true);
        try {
            await onAdd(file);
        } finally {
            setUploading(false);
        }
    }

    function step(direction) {
        setOpenedIndex(index => index === null
            ? index
            : Math.min(Math.max(index + direction, 0), images.length - 1));
    }

    const addTile = (
        <Box
            data-testid="content-image-add"
            role="button"
            aria-label="Add image"
            tabIndex={0}
            onMouseDown={event => {event.preventDefault();fileInput.current?.click()}}
            onDragOver={event => {event.preventDefault();setDragging(true)}}
            onDragLeave={() => setDragging(false)}
            onDrop={event => {
                event.preventDefault();
                setDragging(false);
                upload(event.dataTransfer?.files);
            }}
            sx={{
                width: THUMBNAIL,
                height: THUMBNAIL,
                boxSizing: "border-box",
                flexShrink: 0,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                border: "1px dashed",
                borderColor: dragging ? "primary.main" : "divider",
                borderRadius: "3px",
                color: dragging ? "primary.main" : "text.disabled",
                bgcolor: dragging ? "action.hover" : "transparent",
                cursor: "pointer",
            }}
        >
            {uploading ? <CircularProgress size={18}/> : <AddIcon sx={{fontSize: 20}}/>}
            <input
                ref={fileInput}
                type="file"
                accept="image/png,image/jpeg"
                hidden
                onChange={event => {
                    upload(event.target.files);
                    event.target.value = "";
                }}
            />
        </Box>
    );

    const overlayButton = (label, icon, onPress) => (
        <IconButton
            aria-label={label}
            size="small"
            onMouseDown={event => {event.preventDefault();onPress()}}
            sx={{
                position: "absolute",
                top: "50%",
                left: "50%",
                transform: "translate(-50%, -50%)",
                padding: "4px",
                bgcolor: "rgba(255, 255, 255, 0.85)",
                boxShadow: 1,
                "&:hover": {bgcolor: "background.paper"},
            }}
        >
            {icon}
        </IconButton>
    );

    const thumbnail = (key, source, {onOpen, action, removed = false, fresh = false}) => (
        <Box key={key} sx={{position: "relative", flexShrink: 0}}>
            <Box
                component="img"
                data-testid={`content-image-${key}`}
                src={source}
                alt=""
                loading="lazy"
                onClick={onOpen}
                sx={{
                    width: THUMBNAIL,
                    height: THUMBNAIL,
                    boxSizing: "border-box",
                    objectFit: "cover",
                    borderRadius: "3px",
                    border: fresh ? "1px dashed" : "1px solid",
                    borderColor: fresh ? "primary.main" : "divider",
                    cursor: onOpen ? "pointer" : "default",
                    display: "block",
                    // Crossed out: still there, and plainly on its way out.
                    opacity: removed ? 0.3 : (editing ? 0.75 : 1),
                    filter: removed ? "grayscale(1)" : "none",
                }}
            />
            {action}
        </Box>
    );

    const scroller = (
        <Box
            ref={strip}
            data-testid="content-images"
            onScroll={updateScrollHint}
            sx={{
                display: "flex",
                flexDirection: compact ? "row" : "column",
                gap: "4px",
                flexShrink: 0,
                scrollbarWidth: "none",
                "&::-webkit-scrollbar": {display: "none"},
                ...(compact
                    ? {overflowX: "auto", paddingBottom: "2px", maxWidth: "100%"}
                    : {
                        overflowY: "auto",
                        position: "absolute",
                        top: 0,
                        bottom: 0,
                        right: 0,
                        width: `${THUMBNAIL}px`,
                    }),
            }}
        >
            {/* The way to add one comes first, so it is in the same place however many there are. */}
            {editing && addTile}

            {images.map((image, index) => {
                const removed = removedIds.includes(image.id);
                return thumbnail(image.id, urlOf(image.id, true), {
                    onOpen: editing ? undefined : () => setOpenedIndex(index),
                    removed: removed,
                    action: editing && (removed
                        ? overlayButton("Restore image", <RestoreIcon sx={{fontSize: OVERLAY_ICON}}/>,
                            () => onMarkRemoved(image.id, false))
                        : overlayButton("Remove image",
                            <DeleteIcon width={OVERLAY_ICON} height={OVERLAY_ICON}/>,
                            () => onMarkRemoved(image.id, true))),
                });
            })}

            {/* Added but not written yet: shown straight from the file until the entry is saved. */}
            {pending.map(add => thumbnail(add.key, add.url, {
                fresh: true,
                action: editing && overlayButton("Discard new image",
                    <CloseIcon sx={{fontSize: OVERLAY_ICON}}/>, () => onDropPending(add.key)),
            }))}
        </Box>
    );

    const arrow = (end, icon, placement) => React.createElement(icon, {
        "data-testid": `content-images-${end}`,
        sx: {
            position: "absolute",
            fontSize: 16,
            color: "text.disabled",
            pointerEvents: "none",
            bgcolor: "background.paper",
            borderRadius: "50%",
            ...placement,
        },
    });

    const hints = (
        <>
            {reach.before && (compact
                ? arrow("before", KeyboardArrowLeftIcon, {left: "-2px", top: "50%", transform: "translateY(-50%)"})
                : arrow("before", KeyboardArrowUpIcon, {top: "-2px", left: "50%", transform: "translateX(-50%)"}))}
            {reach.after && (compact
                ? arrow("more", KeyboardArrowRightIcon, {right: "-2px", top: "50%", transform: "translateY(-50%)"})
                : arrow("more", KeyboardArrowDownIcon, {bottom: "-2px", left: "50%", transform: "translateX(-50%)"}))}
        </>
    );

    const opened = openedIndex === null ? null : images[openedIndex];
    const viewerButton = (label, icon, side, onPress) => (
        <IconButton
            aria-label={label}
            onClick={onPress}
            sx={{
                position: "absolute",
                top: "50%",
                transform: "translateY(-50%)",
                ...side,
                bgcolor: "rgba(255, 255, 255, 0.8)",
                boxShadow: 1,
                "&:hover": {bgcolor: "background.paper"},
            }}
        >
            {icon}
        </IconButton>
    );

    const viewer = (
        <Dialog
            open={opened !== null}
            onClose={() => setOpenedIndex(null)}
            maxWidth={false}
            // The frame is the picture: no paper around it beyond its own edges, and never edge
            // to edge, so there is always somewhere to tap to put it away again. The theme turns
            // every dialog full-screen on a narrow screen, which is right for the ones that are
            // worked in and wrong for one that only shows a picture, so it is undone here - and
            // inside the same media query, or the theme's rule wins.
            slotProps={{paper: {sx: theme => ({
                ...VIEWER_PAPER,
                [theme.breakpoints.down("sm")]: VIEWER_PAPER,
            })}}}
        >
            {opened &&
                <Box
                    sx={{position: "relative", display: "flex"}}
                    // A narrow screen steps by swiping and keeps nothing on top of the picture;
                    // a wide one has the arrows instead, whichever way its strip lies. The gesture is measured here rather
                    // than with the app's swipe helper, which refuses to start inside a dialog -
                    // it is there to stop a swipe in a dialog moving the page behind it, and
                    // this swipe belongs to the dialog itself.
                    onTouchStart={narrow
                        ? event => {
                            const touch = event.touches[0];
                            viewerSwipe.current = {x: touch.clientX, y: touch.clientY};
                        }
                        : undefined}
                    onTouchEnd={narrow
                        ? event => {
                            const start = viewerSwipe.current;
                            viewerSwipe.current = null;
                            if (!start) return;

                            const touch = event.changedTouches[0];
                            const across = touch.clientX - start.x;
                            const along = touch.clientY - start.y;
                            if (Math.abs(across) < SWIPE_THRESHOLD
                                || Math.abs(across) < Math.abs(along)) return;

                            step(across < 0 ? 1 : -1);
                        }
                        : undefined}
                >
                    <Box
                        component="img"
                        data-testid="content-image-opened"
                        src={urlOf(opened.id)}
                        alt=""
                        onClick={() => setOpenedIndex(null)}
                        sx={{
                            display: "block",
                            width: "auto",
                            height: "auto",
                            maxWidth: "100%",
                            maxHeight: "100%",
                            cursor: "zoom-out",
                        }}
                    />
                    {!narrow && openedIndex > 0 && viewerButton("Previous image",
                        <KeyboardArrowLeftIcon/>, {left: "6px"}, () => step(-1))}
                    {!narrow && openedIndex < images.length - 1 && viewerButton("Next image",
                        <KeyboardArrowRightIcon/>, {right: "6px"}, () => step(1))}
                </Box>
            }
        </Dialog>
    );

    if (compact) {
        return (
            <Box sx={{position: "relative", maxWidth: "100%", width: "100%"}}>
                {scroller}
                {hints}
                {viewer}
            </Box>
        );
    }

    return (
        <Box sx={{
            position: "relative",
            alignSelf: "stretch",
            flexShrink: 0,
            width: `${THUMBNAIL}px`,
            minHeight: `${THUMBNAIL}px`,
        }}>
            {scroller}
            {hints}
            {viewer}
        </Box>
    );
};
