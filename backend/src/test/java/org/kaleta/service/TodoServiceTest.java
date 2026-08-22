package org.kaleta.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.TodoDao;
import org.kaleta.persistence.entity.Company;
import org.kaleta.persistence.entity.Todo;
import org.kaleta.rest.dto.TodoCreateDto;
import org.kaleta.rest.error.InvalidInputException;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class TodoServiceTest
{
    @InjectMock
    TodoDao todoDao;
    @InjectMock
    CompanyDao companyDao;

    @Inject
    TodoService todoService;

    @Test
    void create_linksFirstUppercaseTicker()
    {
        Company company = new Company();
        company.setId(42L);
        company.setTicker("NVDA");
        when(companyDao.getByTicker("NVDA")).thenReturn(company);

        TodoCreateDto dto = createDto("  Review #NVDA earnings after #AMD report  ");

        org.kaleta.model.Todo created = todoService.create(dto);

        ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
        verify(todoDao).create(captor.capture());
        Todo entity = captor.getValue();
        assertThat(entity.getContent(), is("Review #NVDA earnings after #AMD report"));
        assertThat(entity.getCreatedAt(), is(notNullValue()));
        assertThat(entity.getCompanyId(), is(42L));
        assertThat(created.getContent(), is(entity.getContent()));
        assertThat(created.getCreatedAt(), is(entity.getCreatedAt()));
        assertThat(created.getCompanyId(), is(entity.getCompanyId()));
        verify(companyDao, never()).getByTicker("AMD");
    }

    @Test
    void create_keepsCompanyNullWhenTickerIsMissingUnknownOrLowercase()
    {
        when(companyDao.getByTicker("ABCDE")).thenThrow(NoResultException.class);

        org.kaleta.model.Todo unknown = todoService.create(createDto("#ABCDE review"));
        org.kaleta.model.Todo lowercase = todoService.create(createDto("Review #nvda results"));
        org.kaleta.model.Todo missing = todoService.create(createDto("General research"));

        assertThat(unknown.getCompanyId(), is(nullValue()));
        assertThat(lowercase.getCompanyId(), is(nullValue()));
        assertThat(missing.getCompanyId(), is(nullValue()));
        verify(companyDao).getByTicker("ABCDE");
        verify(companyDao, never()).getByTicker("nvda");
    }

    @Test
    void update_changesContentAndCompanyLinkWithoutChangingCreationTime()
    {
        Todo existing = todo(7L, "Old #AMD review", "2026-09-10T08:30:00", 11L);
        Company company = new Company();
        company.setId(42L);
        company.setTicker("NVDA");
        when(todoDao.get(7L)).thenReturn(existing);
        when(companyDao.getByTicker("NVDA")).thenReturn(company);

        org.kaleta.model.Todo updated = todoService.update(
                7L,
                createDto("  New #NVDA review  ")
        );

        verify(todoDao).save(existing);
        assertThat(existing.getContent(), is("New #NVDA review"));
        assertThat(existing.getCreatedAt(), is(LocalDateTime.parse("2026-09-10T08:30:00")));
        assertThat(existing.getCompanyId(), is(42L));
        assertThat(updated.getId(), is(7L));
        assertThat(updated.getContent(), is(existing.getContent()));
        assertThat(updated.getCreatedAt(), is(existing.getCreatedAt()));
        assertThat(updated.getCompanyId(), is(existing.getCompanyId()));
    }

    @Test
    void update_rejectsUnknownTodo()
    {
        when(todoDao.get(9L)).thenThrow(NoResultException.class);

        assertThrows(
                InvalidInputException.class,
                () -> todoService.update(9L, createDto("Review"))
        );
        verify(todoDao, never()).save(any());
    }

    @Test
    void getAll_mapsDaoResults()
    {
        Todo first = todo(1L, "First", "2026-08-22T10:00:00", 10L);
        Todo second = todo(2L, "Second", "2026-08-23T11:00:00", null);
        when(todoDao.list()).thenReturn(List.of(first, second));

        List<org.kaleta.model.Todo> result = todoService.getAll();

        assertThat(result.stream().map(org.kaleta.model.Todo::getId).toList(), contains(1L, 2L));
        assertThat(result.get(0).getContent(), is("First"));
        assertThat(result.get(0).getCompanyId(), is(10L));
        assertThat(result.get(1).getCreatedAt(), is(LocalDateTime.parse("2026-08-23T11:00:00")));
    }

    @Test
    void delete()
    {
        when(todoDao.get(8L)).thenReturn(todo(8L, "Existing", "2026-08-22T10:00:00", null));
        when(todoDao.get(9L)).thenThrow(NoResultException.class);

        todoService.delete(8L);
        verify(todoDao).delete(8L);

        assertThrows(InvalidInputException.class, () -> todoService.delete(9L));
        verify(todoDao, never()).delete(9L);
    }

    private TodoCreateDto createDto(String content)
    {
        TodoCreateDto dto = new TodoCreateDto();
        dto.setContent(content);
        return dto;
    }

    private Todo todo(Long id, String content, String createdAt, Long companyId)
    {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setContent(content);
        todo.setCreatedAt(LocalDateTime.parse(createdAt));
        todo.setCompanyId(companyId);
        return todo;
    }
}
