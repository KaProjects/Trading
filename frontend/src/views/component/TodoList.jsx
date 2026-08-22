import React, {useEffect, useState} from "react";
import axios from "axios";
import {
    Box,
    CircularProgress,
    IconButton,
    List,
    ListItem,
    ListItemButton,
    ListItemText,
    ListSubheader,
    Tooltip,
} from "@mui/material";
import useMediaQuery from "@mui/material/useMediaQuery";
import ControlPointIcon from "@mui/icons-material/ControlPoint";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import TaskAltIcon from "@mui/icons-material/TaskAlt";
import {backend} from "../../properties";
import {formatError} from "../../service/FormattingService";
import {AddTodoDialog} from "../../dialog/AddTodoDialog";
import {SnackbarErrorAlert} from "./SnackbarErrorAlert";
import {COMPANY_SELECTOR_SIDEBAR_BREAKPOINT} from "./CompanySelector";

function sortTodos(todos) {
    return [...todos].sort((left, right) =>
        left.createdAt.localeCompare(right.createdAt) || left.id - right.id
    );
}

export const TodoList = props => {
    const narrowScreen = useMediaQuery(`(max-width:${COMPANY_SELECTOR_SIDEBAR_BREAKPOINT}px)`);
    const visible = !narrowScreen || props.active;
    const [todos, setTodos] = useState([]);
    const [loaded, setLoaded] = useState(false);
    const [alert, setAlert] = useState(null);
    const [openAddDialog, setOpenAddDialog] = useState(false);
    const [todoToEdit, setTodoToEdit] = useState(null);

    useEffect(() => {
        if (!visible) return;

        let active = true;
        setLoaded(false);
        axios.get(backend + "/todo")
            .then(response => {
                if (!active) return;
                setTodos(sortTodos(response.data));
                setAlert(null);
                setLoaded(true);
            })
            .catch(error => {
                if (!active) return;
                setAlert(formatError(error));
                setLoaded(true);
            });

        return () => {
            active = false;
        };
    }, [visible]);

    if (!visible) return null;

    function addTodo(todo) {
        setTodos(previous => sortTodos([...previous, todo]));
    }

    function updateTodo(updatedTodo) {
        setTodos(previous => sortTodos(previous.map(todo =>
            todo.id === updatedTodo.id ? updatedTodo : todo
        )));
    }

    function closeTodoDialog() {
        setOpenAddDialog(false);
        setTodoToEdit(null);
    }

    function removeTodo(todoId) {
        axios.delete(backend + "/todo/" + todoId)
            .then(() => setTodos(previous => previous.filter(todo => todo.id !== todoId)))
            .catch(error => setAlert(formatError(error)));
    }

    function selectCompany(todo) {
        const company = (props.companyLists?.all ?? []).find(item => item.id === todo.companyId);
        if (!company) return;

        props.setCompanyListSelectorValue?.("all");
        props.setCompanySelectorValue(company);
        props.onCompanySelected?.();
    }

    const listHeader = (
        <ListSubheader component="div" sx={{boxShadow: 1, borderRadius: 2}}>
            <Box sx={{display: "flex", alignItems: "center", justifyContent: "space-between", color: "grey"}}>
                <Box component="span" sx={{fontSize: "16px"}}>Todos</Box>
                <Tooltip title="Add todo">
                    <IconButton
                        aria-label="Add todo"
                        size="small"
                        onClick={() => {
                            setTodoToEdit(null);
                            setOpenAddDialog(true);
                        }}
                    >
                        <ControlPointIcon sx={{color: "lightgreen", fontSize: 20}}/>
                    </IconButton>
                </Tooltip>
            </Box>
        </ListSubheader>
    );

    return (
        <>
            <List
                dense
                aria-label="Research todos"
                subheader={listHeader}
                sx={{
                    maxWidth: narrowScreen ? "800px" : "200px",
                    position: narrowScreen ? "static" : "absolute",
                    top: 0,
                    right: narrowScreen ? "auto" : 0,
                    zIndex: 1,
                    margin: narrowScreen ? "2px auto 0" : "2px 0 0",
                    width: narrowScreen ? "100%" : "200px",
                    maxHeight: "calc(100dvh - var(--main-bar-height, 48px) - 16px)",
                    overflowY: "auto",
                    overscrollBehavior: "contain",
                    bgcolor: "background.paper",
                    boxShadow: 1,
                    borderRadius: 2,
                }}
            >
                {!loaded &&
                    <Box sx={{display: "flex", justifyContent: "center", padding: 2}}>
                        <CircularProgress size={22}/>
                    </Box>
                }
                {loaded && todos.length === 0 &&
                    <ListItem>
                        <ListItemText
                            primary="No todos"
                            slotProps={{primary: {sx: {color: "text.secondary", textAlign: "center"}}}}
                        />
                    </ListItem>
                }
                {loaded && todos.map(todo => {
                    const company = (props.companyLists?.all ?? []).find(item => item.id === todo.companyId);

                    return (
                        <ListItem
                            key={todo.id}
                            disablePadding
                            sx={{
                                alignItems: "stretch",
                                "& .todo-actions": {
                                    opacity: narrowScreen ? 1 : 0,
                                    pointerEvents: narrowScreen ? "auto" : "none",
                                    transition: "opacity 120ms ease-in-out",
                                },
                                "&:hover .todo-actions, &:focus-within .todo-actions": {
                                    opacity: 1,
                                    pointerEvents: "auto",
                                },
                            }}
                        >
                            <ListItemButton
                                aria-label={`Edit todo ${todo.id}`}
                                onClick={() => setTodoToEdit(todo)}
                                sx={{minWidth: 0, flex: "1 1 auto", alignItems: "stretch", flexDirection: "column", padding: "4px 8px 3px"}}
                            >
                                <ListItemText
                                    primary={todo.content}
                                    slotProps={{
                                        primary: {sx: {fontSize: "13px", overflowWrap: "anywhere"}},
                                    }}
                                />
                            </ListItemButton>
                            <Box className="todo-actions" sx={{display: "flex", alignItems: "center", flexDirection: "column", justifyContent: "center"}}>
                                {company &&
                                    <Tooltip title={`Open ${company.ticker} research`}>
                                        <IconButton aria-label={`Open ${company.ticker}`} size="small" onClick={() => selectCompany(todo)}>
                                            <OpenInNewIcon sx={{fontSize: 17}}/>
                                        </IconButton>
                                    </Tooltip>
                                }
                                <Tooltip title="Complete">
                                    <IconButton aria-label={`Complete todo ${todo.id}`} size="small" onClick={() => removeTodo(todo.id)}>
                                        <TaskAltIcon sx={{fontSize: 17, color: "success.main"}}/>
                                    </IconButton>
                                </Tooltip>
                            </Box>
                        </ListItem>
                    );
                })}
            </List>
            <AddTodoDialog
                open={openAddDialog || todoToEdit !== null}
                todo={todoToEdit}
                handleClose={closeTodoDialog}
                onCreated={addTodo}
                onUpdated={updateTodo}
            />
            <SnackbarErrorAlert error={alert} open={alert !== null} onClose={() => setAlert(null)}/>
        </>
    );
};
