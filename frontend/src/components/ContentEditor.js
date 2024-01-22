import React, {useCallback, useMemo, useState} from 'react'
import {Editable, Slate, useSlate, withReact} from 'slate-react'
import {createEditor, Editor, Element as SlateElement, Transforms} from 'slate'
import {withHistory} from 'slate-history'
import {css, cx} from '@emotion/css'
import {properties} from "../properties";
import axios from "axios";

const LIST_TYPES = ['numbered-list', 'bulleted-list']
const TEXT_ALIGN_TYPES = ['left', 'center', 'right', 'justify']
const DEFAULT_VALUE = [{type: 'paragraph', children: [{ text: '' }],}]


const ContentEditor = (props) => {
    const {record} = props
    const renderElement = useCallback(props => <Element {...props} />, [])
    const renderLeaf = useCallback(props => <Leaf {...props} />, [])
    const editor = useMemo(() => withHistory(withReact(createEditor())), [])

    const [editing, setEditing] = useState(false);
    const [value, setValue] = useState(DEFAULT_VALUE);

    function handleUnFocus() {
        setEditing(false)
        const data = {id: record.id, content: JSON.stringify(value)}
        const url = properties.protocol + "://" + properties.host + ":" + properties.port + "/record";
        axios.put(url, data)
            .then((response) => {
                props.handleUpdate(value)
            }).catch((error) => {
                console.error(error)
            })
    }
    return (
        <Slate editor={editor} initialValue={record.content ? JSON.parse(record.content) : DEFAULT_VALUE}
               onChange={value => {
                   const isAstChange = editor.operations.some(op => 'set_selection' !== op.type0)
                   if (isAstChange) setValue(value)
               }}
        >
            {editing &&
                <Toolbar>
                    <MarkButton format="bold" icon="format_bold"/>
                    <MarkButton format="italic" icon="format_italic"/>
                    <MarkButton format="underline" icon="format_underlined"/>
                    <MarkButton format="code" icon="code"/>
                    <BlockButton format="heading-one" icon="looks_one"/>
                    <BlockButton format="heading-two" icon="looks_two"/>
                    <BlockButton format="block-quote" icon="format_quote"/>
                    <BlockButton format="numbered-list" icon="format_list_numbered"/>
                    <BlockButton format="bulleted-list" icon="format_list_bulleted"/>
                    <BlockButton format="left" icon="format_align_left"/>
                    <BlockButton format="center" icon="format_align_center"/>
                    <BlockButton format="right" icon="format_align_right"/>
                    <BlockButton format="justify" icon="format_align_justify"/>
                </Toolbar>
            }
            <Editable
                renderElement={renderElement}
                renderLeaf={renderLeaf}
                placeholder="write a content"
                style={{margin: "0 5px 0 5px", paddingBottom: editing ? "10px" : "0"}}
                onFocus={() => setEditing(true)}
                onBlur={handleUnFocus}
            />
        </Slate>
    )
}
export default ContentEditor

const toggleBlock = (editor, format) => {
    const isActive = isBlockActive(
        editor,
        format,
        TEXT_ALIGN_TYPES.includes(format) ? 'align' : 'type'
    )
    const isList = LIST_TYPES.includes(format)

    Transforms.unwrapNodes(editor, {
        match: n =>
            !Editor.isEditor(n) &&
            SlateElement.isElement(n) &&
            LIST_TYPES.includes(n.type) &&
            !TEXT_ALIGN_TYPES.includes(format),
        split: true,
    })
    let newProperties
    if (TEXT_ALIGN_TYPES.includes(format)) {
        newProperties = {
            align: isActive ? undefined : format,
        }
    } else {
        newProperties = {
            type: isActive ? 'paragraph' : isList ? 'list-item' : format,
        }
    }
    Transforms.setNodes(editor, newProperties)

    if (!isActive && isList) {
        const block = { type: format, children: [] }
        Transforms.wrapNodes(editor, block)
    }
}

const toggleMark = (editor, format) => {
    const isActive = isMarkActive(editor, format)

    if (isActive) {
        Editor.removeMark(editor, format)
    } else {
        Editor.addMark(editor, format, true)
    }
}

const isBlockActive = (editor, format, blockType = 'type') => {
    const { selection } = editor
    if (!selection) return false

    const [match] = Array.from(
        Editor.nodes(editor, {
            at: Editor.unhangRange(editor, selection),
            match: n => !Editor.isEditor(n) && SlateElement.isElement(n) && n[blockType] === format,
        })
    )

    return !!match
}

const isMarkActive = (editor, format) => {
    const marks = Editor.marks(editor)
    return marks ? marks[format] === true : false
}

const Element = ({ attributes, children, element }) => {
    const style = { textAlign: element.align }
    switch (element.type) {
        case 'block-quote':
            return (<blockquote style={style} {...attributes}>{children}</blockquote>)
        case 'bulleted-list':
            return (<ul style={style} {...attributes}>{children}</ul>)
        case 'heading-one':
            return (<h1 style={style} {...attributes}>{children}</h1>)
        case 'heading-two':
            return (<h2 style={style} {...attributes}>{children}</h2>)
        case 'list-item':
            return (<li style={style} {...attributes}>{children}</li>)
        case 'numbered-list':
            return (<ol style={style} {...attributes}>{children}</ol>)
        default:
            return (<p style={style} {...attributes}>{children}</p>)
    }
}

const Leaf = ({ attributes, children, leaf }) => {
    if (leaf.bold) {children = <strong>{children}</strong>}
    if (leaf.code) {children = <code>{children}</code>}
    if (leaf.italic) {children = <em>{children}</em>}
    if (leaf.underline) {children = <u>{children}</u>}
    return <span {...attributes}>{children}</span>
}

const BlockButton = ({ format, icon }) => {
    const editor = useSlate()
    return (
        <Button active={isBlockActive(editor, format, TEXT_ALIGN_TYPES.includes(format) ? 'align' : 'type')}
                onMouseDown={event => {event.preventDefault();toggleBlock(editor, format)}}>
            <Icon>{icon}</Icon>
        </Button>
    )
}

const MarkButton = ({ format, icon }) => {
    const editor = useSlate()
    return (
        <Button active={isMarkActive(editor, format)}
                onMouseDown={event => {event.preventDefault();toggleMark(editor, format)}}>
            <Icon>{icon}</Icon>
        </Button>
    )
}

const Button = React.forwardRef(({className, active, reversed, ...props}, ref) => (
    <span{...props} ref={ref}
         className={cx(className, css`cursor: pointer;color: ${reversed ? active ? 'white' : '#aaa' : active ? 'black' : '#ccc'};`)}
    />
))

const Icon = React.forwardRef(({className, ...props}, ref) => (
    <span{...props} ref={ref}
         className={cx('material-icons', className, css`font-size: 18px;vertical-align: text-bottom;`)}
    />
))

const Menu = React.forwardRef(({ className, ...props }, ref) => (
    <div{...props} ref={ref}
        className={cx(className, css` & > * {display: inline-block;width: 30px;}`)}
    />
))

const Toolbar = React.forwardRef(({ className, ...props }, ref) => (
    <Menu {...props} ref={ref}
          className={cx(className, css`position: relative;padding: 5px; margin: -5px 5px -5px 5px;border-bottom: 2px solid #eee;`)}
    />
))
