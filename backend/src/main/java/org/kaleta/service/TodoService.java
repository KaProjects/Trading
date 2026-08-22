package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.TodoDao;
import org.kaleta.rest.dto.TodoCreateDto;
import org.kaleta.rest.error.InvalidInputException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class TodoService
{
    private static final Pattern COMPANY_TICKER = Pattern.compile("(?<!\\S)#([A-Z]{1,5})(?=\\s|$)");

    @Inject
    TodoDao todoDao;
    @Inject
    CompanyDao companyDao;

    public List<org.kaleta.model.Todo> getAll()
    {
        return todoDao.list().stream().map(this::from).toList();
    }

    public org.kaleta.model.Todo create(TodoCreateDto dto)
    {
        org.kaleta.persistence.entity.Todo todo = new org.kaleta.persistence.entity.Todo();
        todo.setCreatedAt(LocalDateTime.now().withNano(0));
        apply(todo, dto);
        todoDao.create(todo);
        return from(todo);
    }

    public org.kaleta.model.Todo update(Long todoId, TodoCreateDto dto)
    {
        org.kaleta.persistence.entity.Todo todo = findTodo(todoId);
        apply(todo, dto);
        todoDao.save(todo);
        return from(todo);
    }

    public void delete(Long todoId)
    {
        findTodo(todoId);
        todoDao.delete(todoId);
    }

    private org.kaleta.persistence.entity.Todo findTodo(Long todoId)
    {
        try {
            return todoDao.get(todoId);
        } catch (NoResultException exception) {
            throw new InvalidInputException("todo with id '" + todoId + "' not found");
        }
    }

    private void apply(org.kaleta.persistence.entity.Todo todo, TodoCreateDto dto)
    {
        todo.setContent(dto.getContent().trim());
        todo.setCompanyId(findCompanyId(todo.getContent()));
    }

    private Long findCompanyId(String content)
    {
        Matcher matcher = COMPANY_TICKER.matcher(content);
        if (!matcher.find()) {
            return null;
        }

        try {
            return companyDao.getByTicker(matcher.group(1)).getId();
        } catch (NoResultException exception) {
            return null;
        }
    }

    private org.kaleta.model.Todo from(org.kaleta.persistence.entity.Todo entity)
    {
        org.kaleta.model.Todo todo = new org.kaleta.model.Todo();
        todo.setId(entity.getId());
        todo.setContent(entity.getContent());
        todo.setCreatedAt(entity.getCreatedAt());
        todo.setCompanyId(entity.getCompanyId());
        return todo;
    }
}
