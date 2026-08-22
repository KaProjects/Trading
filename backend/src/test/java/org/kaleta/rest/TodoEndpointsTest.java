package org.kaleta.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.kaleta.persistence.api.TodoDao;
import org.kaleta.persistence.entity.Todo;
import org.kaleta.rest.dto.TodoCreateDto;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kaleta.framework.Assert.ExpectedViolation.NOT_NULL;
import static org.kaleta.framework.Assert.ExpectedViolation.VALID_ID;
import static org.kaleta.framework.Assert.delete400;
import static org.kaleta.framework.Assert.deleteValidationError;
import static org.kaleta.framework.Assert.postValidationError;
import static org.kaleta.framework.Assert.put400;
import static org.kaleta.framework.Assert.putValidationError;

@QuarkusTest
class TodoEndpointsTest
{
    private static final String PATH = "/todo";

    @Inject
    TodoDao todoDao;

    @Test
    void create()
    {
        TodoCreateDto dto = dto("Review #CRE product strategy");

        org.kaleta.model.Todo created = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post(PATH)
                .then().log().ifError()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract().as(org.kaleta.model.Todo.class);

        Todo entity = todoDao.get(created.getId());
        assertThat(entity.getContent(), is(dto.getContent()));
        assertThat(entity.getCreatedAt(), is(notNullValue()));
        assertThat(created.getCreatedAt(), is(entity.getCreatedAt()));
        assertThat(entity.getCompanyId(), is(1565L));
        todoDao.delete(created.getId());
    }

    @Test
    void create_invalidParameters()
    {
        postValidationError(PATH, null, NOT_NULL);

        TodoCreateDto dto = dto("Review");
        dto.setContent(null);
        postValidationError(PATH, dto, "must not be blank");
        dto.setContent("   ");
        postValidationError(PATH, dto, "must not be blank");
    }

    @Test
    void getAll()
    {
        Todo newer = todo("Newer list endpoint", "2026-09-11T10:00:00");
        Todo older = todo("Older list endpoint", "2026-09-10T10:00:00");
        todoDao.create(newer);
        todoDao.create(older);

        List<Long> ids = given().when().get(PATH)
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("find { it.id == " + older.getId() + " }.content", is(older.getContent()))
                .body("find { it.id == " + older.getId() + " }.companyId", is(nullValue()))
                .extract().jsonPath().getList("id", Long.class);

        assertThat(ids.indexOf(older.getId()) < ids.indexOf(newer.getId()), is(true));
        todoDao.delete(older.getId());
        todoDao.delete(newer.getId());
    }

    @Test
    void update()
    {
        Todo todo = todo("Original task", "2026-09-11T10:00:00");
        todoDao.create(todo);
        TodoCreateDto dto = dto("Review #CRE after results");

        org.kaleta.model.Todo updated = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when().put(PATH + "/" + todo.getId())
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().as(org.kaleta.model.Todo.class);

        Todo entity = todoDao.get(todo.getId());
        assertThat(updated.getId(), is(todo.getId()));
        assertThat(updated.getContent(), is(dto.getContent()));
        assertThat(updated.getCreatedAt(), is(todo.getCreatedAt()));
        assertThat(updated.getCompanyId(), is(1565L));
        assertThat(entity.getContent(), is(dto.getContent()));
        assertThat(entity.getCreatedAt(), is(todo.getCreatedAt()));
        assertThat(entity.getCompanyId(), is(1565L));
        todoDao.delete(todo.getId());
    }

    @Test
    void update_invalidParameters()
    {
        TodoCreateDto dto = dto("Review");
        putValidationError(PATH + "/0", dto, VALID_ID);
        putValidationError(PATH + "/1", null, NOT_NULL);

        dto.setContent(" ");
        putValidationError(PATH + "/1", dto, "must not be blank");
        dto.setContent("Review");
        put400(PATH + "/4294967295", dto, "todo with id '4294967295' not found");
    }

    @Test
    void delete()
    {
        Todo todo = todo("Delete endpoint", "2026-09-12T10:00:00");
        todoDao.create(todo);

        given().when().delete(PATH + "/" + todo.getId())
                .then().log().ifError()
                .statusCode(Response.Status.OK.getStatusCode());

        delete400(PATH + "/" + todo.getId(), "todo with id '" + todo.getId() + "' not found");
    }

    @Test
    void delete_invalidParameters()
    {
        deleteValidationError(PATH + "/0", VALID_ID);
        delete400(PATH + "/4294967295", "todo with id '4294967295' not found");
    }

    private TodoCreateDto dto(String content)
    {
        TodoCreateDto dto = new TodoCreateDto();
        dto.setContent(content);
        return dto;
    }

    private Todo todo(String content, String createdAt)
    {
        Todo todo = new Todo();
        todo.setContent(content);
        todo.setCreatedAt(LocalDateTime.parse(createdAt));
        return todo;
    }
}
