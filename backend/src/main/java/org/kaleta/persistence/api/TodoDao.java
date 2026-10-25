package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.Todo;

public interface TodoDao extends EntityDao<Todo>
{
    void delete(Long todoId);
}
