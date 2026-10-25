import {useState} from "react";
import axios from "axios";
import {formatError} from "../../service/FormattingService";

/** No pictures added, none crossed out. */
export const EMPTY_STAGED = {adds: [], removes: []}

/**
 * The pictures added to or crossed out of an entry while it is being written. They wait here
 * until the entry is saved - adding one is not a save, and crossing one out is not a delete -
 * and are dropped whole when the writing is cancelled.
 *
 * `uploadUrl` and `deleteUrl` say where the pictures of this kind of entry live, and `applied`
 * hands the caller what changed so it can put the entry's own state right.
 */
export function useStagedImages({uploadUrl, deleteUrl, applied, setAlert}) {
    const [staged, setStaged] = useState({})

    const stagedFor = entryId => staged[entryId] ?? EMPTY_STAGED

    function stageImage(entryId, file) {
        const pending = {key: `${Date.now()}-${file.name}`, file: file, url: URL.createObjectURL(file)}
        setStaged(previous => {
            const current = previous[entryId] ?? EMPTY_STAGED
            return {...previous, [entryId]: {...current, adds: [...current.adds, pending]}}
        })
    }

    function dropStagedImage(entryId, key) {
        setStaged(previous => {
            const current = previous[entryId] ?? EMPTY_STAGED
            current.adds.filter(add => add.key === key).forEach(add => URL.revokeObjectURL(add.url))
            return {...previous, [entryId]: {...current, adds: current.adds.filter(add => add.key !== key)}}
        })
    }

    /** Crossing a picture out is not deleting it; that waits for the entry to be saved. */
    function markImageRemoved(entryId, imageId, removed) {
        setStaged(previous => {
            const current = previous[entryId] ?? EMPTY_STAGED
            return {
                ...previous,
                [entryId]: {
                    ...current,
                    removes: removed
                        ? [...current.removes, imageId]
                        : current.removes.filter(id => id !== imageId),
                },
            }
        })
    }

    function discardStaged(entryId) {
        setStaged(previous => {
            const current = previous[entryId]
            if (!current) return previous

            current.adds.forEach(add => URL.revokeObjectURL(add.url))
            const next = {...previous}
            delete next[entryId]
            return next
        })
    }

    /** Everything staged for one entry, written in one go. Returns an error, or nothing. */
    async function commitStaged(entryId) {
        const {adds, removes} = stagedFor(entryId)
        if (adds.length === 0 && removes.length === 0) return null

        try {
            for (const imageId of removes) {
                await axios.delete(deleteUrl(imageId))
            }
            const added = []
            for (const add of adds) {
                const data = new FormData()
                data.append("file", add.file)
                added.push((await axios.post(uploadUrl(entryId), data)).data)
            }

            // Newest first, the way they come back from the server.
            applied(entryId, {added: added.reverse(), removedIds: removes})
            discardStaged(entryId)
            return null
        } catch (error) {
            const formatted = formatError(error)
            setAlert(formatted)
            return formatted
        }
    }

    return {stagedFor, stageImage, dropStagedImage, markImageRemoved, discardStaged, commitStaged}
}
