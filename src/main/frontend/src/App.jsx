import React, { useEffect, useState } from 'react';
import { v4 as uuid } from 'uuid';
import { Box, Heading, Text, Button } from 'rebass';
import { Input } from '@rebass/forms';

const TODOS_ENDPOINT = '/todos';
const USER_ENDPOINT = '/user';
const LOGOUT_ENDPOINT = '/logout';
const SESSION_ERROR = Symbol();
const SESSION_REFRESH_INTERVAL = 5000; // milliseconds

export default () => {
  const [error, setError] = useState(SESSION_ERROR);
  const [user, setUser] = useState({});

  useEffect(() => {
    fetchUserData({ setError, setUser });
  }, [setError, setUser]);

  return (
    <Box sx={{
      p: 4,
      color: 'text',
      bg: 'background',
      fontFamily: 'body',
      fontWeight: 'body',
      lineHeight: 'body',
    }}>
      <Heading mb={4}>Identity-aware SPA</Heading>
      <Text mb={3}>Built with Spring Boot, Spring Security and React</Text>
      <Session invalid={error && error === SESSION_ERROR} setError={setError} setUser={setUser}>
        {error && <Error error={error} setError={setError}/>}
        <User user={user} setError={setError} setUser={setUser}/>
        <Todos user={user} setError={setError}/>
      </Session>
    </Box>
  );
}

function Session(props) {
  if (props.invalid) {
    return (
      <>
        <div className="form-row">
          <h2>Log in to see your todos.</h2>
        </div>
        <div className="form-row">
          <Button onClick={() => window.location = '/login'}>Log In</Button>
        </div>
      </>
    );
  }

  return (
    <>
      {props.children}
    </>
  );
}

function Error(props) {

  const clear = () => props.setError("");

  return (
    <>
      <h2>Error: </h2>
      <pre className="error">
        {typeof props.error !== 'string' ?
          (props.error.stack ? props.error.stack : JSON.stringify(props.error)) :
          props.error
        }
      </pre>
      <Button onClick={() => clear()}>Clear</Button>
    </>
  );
}

function User(props) {

  useEffect(() => {
    const id = setInterval(() => fetchUserData(props), SESSION_REFRESH_INTERVAL);
    return () => clearInterval(id);
  }, [props.setUser, props.setError]);

  const logout = () => fetch(LOGOUT_ENDPOINT, {
    method: 'POST',
    headers: createPostHeaders({})
  }).then(response => handleApiResponse(LOGOUT_ENDPOINT, response))
    .then(() => Promise.reject(SESSION_ERROR))
    .catch(e => props.setError(e));

  return (
    <>
      <h2>User details:</h2>
      <pre className="user">
        {JSON.stringify(props.user, null, 4)}
      </pre>
      <div className="form-row">
        <Button variant='outline' mb={2} onClick={() => fetchUserData(props)}>Refresh User Details</Button>
      </div>
      <div className="form-row">
        <Button mb={4} onClick={() => logout()}>Logout</Button>
      </div>
    </>
  );
}

function fetchUserData(props) {
  return getApiResponse(USER_ENDPOINT)
    .then(response => handleApiResponse(USER_ENDPOINT, response))
    .then(data => {
      props.setUser(data);
      props.setError(null);
    })
    .catch(e => {
      props.setError(e);
      return Promise.reject(e).catch(() => {
      });
    });
}

function Todos(props) {
  if (props.user.groups) {
    if (props.user.groups.includes('sre')) {
      return (
        <AdminTodos setError={props.setError}/>
      );
    }

    return (
      <UserTodos setError={props.setError}/>
    );
  }

  return (
    <></>
  );
}

function AdminTodos(props) {
  const [data, setData] = useState({ todos: [], user: null });

  const query = e => {
    e.preventDefault();

    const username = new FormData(e.target).get('username');
    if (username) {
      const endpoint = `${TODOS_ENDPOINT}/${username}`;

      getApiResponse(endpoint)
        .then(response => handleApiResponse(endpoint, response))
        .then(userTodos => setData({ todos: userTodos.todos, user: username }))
        .catch(e => props.setError(e));
    }
    e.target.reset();
  };

  const clear = () => setData({ todos: [], user: null });

  let content;
  if (data.user) {
    content = (
      <>
        <h2>Todos for {`${data.user}`}</h2>
        <TodoList todos={data.todos}/>
        <div className="form-row">
          <Button onClick={() => clear()}>Clear</Button>
        </div>
      </>
    );
  } else {
    content = (<></>);
  }

  return (
    <>
      <h2>Todos Administration:</h2>
      <form id="query-user-todos" onSubmit={e => query(e)}>
        <div className="form-row">
          <Input width={512} mb={2} autoComplete="off" id="username" name="username" type="text"
                 placeholder="Username"/>
        </div>
        <div className="form-row">
          <Button mb={4} type="submit">Get Todos</Button>
        </div>
      </form>
      {content}
    </>
  );
}

function UserTodos(props) {

  const [data, setData] = useState({ todos: [] });

  useEffect(() => {
    getApiResponse(TODOS_ENDPOINT)
      .then(response => handleApiResponse(TODOS_ENDPOINT, response))
      .then(newData => setData(newData))
      .catch(e => props.setError(e));
  }, [props.setError]);

  const create = e => {
    e.preventDefault();

    const formData = new FormData(e.target);
    const newTodo = {
      id: uuid(),
      content: formData.get('todo-content') || ''
    };
    e.target.reset();

    const requestConfig = {
      method: 'POST',
      body: JSON.stringify(newTodo),
      headers: createPostHeaders({ 'Content-Type': 'application/json' })
    };

    fetch(TODOS_ENDPOINT, requestConfig)
      .then(response => {
        if (!response.ok) {
          throw response;
        }
        handleApiResponse(TODOS_ENDPOINT, response);
      })
      .then(() => {
        const newTodos = data.todos.splice(0);
        newTodos.push(newTodo);
        setData({ todos: newTodos });
      })
      .catch(e => setData({ todos: data.todos, error: e }));
  };

  return (
    <>
      <h2>Todos:</h2>
      <TodoList todos={data.todos}/>
      <form id="create-todo" onSubmit={e => create(e)}>
        <div className="form-row">
          <Input width={512} mb={2} autoComplete="off" id="todo-content" name="todo-content" type="text"
                 placeholder="Content"/>
        </div>
        <div className="form-row">
          <Button mb={4} type="submit">Create</Button>
        </div>
      </form>
      {renderTodoErrors(data)}
    </>
  );
}

function renderTodoErrors(data) {
  if (data.error) {
    return (
      <span>Error response creating todo: {data.error.status} - {data.error.statusText}</span>
    );
  }
}

function TodoList(props) {
  if (props.todos.length > 0) {
    return (
      <ul>
        {props.todos.map(todo => <li key={todo.id} id={todo.id}>{todo.content}</li>)}
      </ul>
    );
  }

  return (
    <Text mb={2}>No todos</Text>
  );
}

function createPostHeaders(headers) {
  return Object.assign({}, {
    'Accept': 'application/json',
    'X-Xsrf-Token': csrfToken()
  }, headers);
}

function csrfToken() {
  return document.cookie.replace(/(?:(?:^|.*;\s*)XSRF-TOKEN\s*=\s*([^;]*).*$)|^.*$/, "$1");
}

function getApiResponse(endpoint) {
  return fetch(endpoint, {
    method: 'GET',
    headers: {
      'Accept': 'application/json'
    }
  });
}

function handleApiResponse(endpoint, response) {

  if (response.status === 401) {
    return Promise.reject(SESSION_ERROR);
  }

  const contentType = response.headers.get('Content-Type');
  const emptyBody = response.headers.get('Content-Length') === "0";

  if (emptyBody) {
    return Promise.resolve({});
  }

  if (contentType === 'application/json') {
    return response.json();
  }

  return response.text().then(content => {
    return Promise.reject(`Unexpected response from ${endpoint}\n\n${responseToString(response, content)}`);
  });
}

function responseToString(response, content) {
  let headersContent = "";
  for (const pair of response.headers) {
    headersContent += `${pair[0]}: ${pair[1]}\n`;
  }
  return `${response.status} ${response.statusText}\n${headersContent}\n\n${content}`;
}
