package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.kaleta.persistence.api.TodoDao;
import org.kaleta.persistence.entity.Todo;

import java.util.List;

@ApplicationScoped
public class TodoDaoImpl extends EntityDaoImpl<Todo> implements TodoDao
{
    @Override
    protected Class<Todo> getEntityClass()
    {
        return Todo.class;
    }

    @Override
    public List<Todo> list()
    {
        return entityManager.createQuery(
                selectQuery + "ORDER BY t.createdAt, t.id",
                Todo.class
        ).getResultList();
    }

    @Transactional
    @Override
    public void delete(Long todoId)
    {
        Todo managed = entityManager.find(Todo.class, todoId);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
