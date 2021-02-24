import React, { useEffect, useState } from 'react';
import { v4 as uuid } from 'uuid';
import { Box, Heading, Text, Button } from 'rebass';
import { Input } from '@rebass/forms';

const API_BASE_URL = `${process.env.API_BASE_URL}`;
const HOME_PAGE = `https://localhost:9001`;
const NON_INTERACTIVE_LOGIN_ENDPOINT = `${API_BASE_URL}/login/non-interactive`;
const LOGIN_ENDPOINT = `${API_BASE_URL}/login`;
const TODOS_ENDPOINT = `${API_BASE_URL}/todos`;
const USER_ENDPOINT = `${API_BASE_URL}/user`;
const LOGOUT_ENDPOINT = `${API_BASE_URL}/logout`;
const LOGOUT_SESSION_ONLY = `${API_BASE_URL}/pa/oidc/logout`;
const SESSION_ERROR = Symbol();
const SESSION_REFRESH_INTERVAL = 5000; // milliseconds

export default () => {
  const [error, setError] = useState(SESSION_ERROR);
  const [user, setUser] = useState({});

  /*
    The call to /user in PingAccess will return JSON with the username and array of groups, formatted in the same way as
    the pre-onboarded SPA /user endpoint was. However, the data is populated with identity information from the OpenID
    provider.
   */
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
          <Button onClick={() => login(props.setError, props.setUser)}>Log In</Button>
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

function login(setError, setUser) {
  /*
    Call the /login/non-interactive endpoint in PingAccess to see if the user already has a PingFederate session.
     - If so, a series of 302s to PingFederate and back will establish a PingAccess session cookie. A subsequent call
     to /users will return a 200 without requiring the page to navigate anywhere, re-render, or relinquish control of
     the UX.
     - If not, the call to /users will return a 401 and the application must redirect the end user to the /login endpoint
      in PingAccess to establish a PingFederate session and PingAccess session cookie.
   */
  fetch(NON_INTERACTIVE_LOGIN_ENDPOINT, {
    method: 'GET',
    mode: 'no-cors',
    credentials: 'include'
  }).then(() => fetchUserDataWithoutCatch({ setError, setUser }))
    .catch(() => window.location = LOGIN_ENDPOINT);
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
        <Button mb={2} onClick={() => logout()}>Logout</Button>
        <Button mb={4} mx={4} onClick={() => logoutPAOnly()}>Logout, This App Only</Button>
      </div>
    </>
  );
}

function logout() {
  window.location = LOGOUT_ENDPOINT;
}

function logoutPAOnly() {
  fetch(LOGOUT_SESSION_ONLY, {
    method: 'GET',
    mode: 'no-cors',
    credentials: 'include'
  }).then(() => window.location = HOME_PAGE);
}

function fetchUserData(props) {
  return fetchUserDataWithoutCatch(props)
    .catch(e => {
      props.setError(e);
      return Promise.reject(e).catch(() => {
      });
    });
}

/*
  The fetchUserData function has been split to have the API call inside a function without a catch to facilitate the
  /login/non-interactive call from the login function.
 */
function fetchUserDataWithoutCatch(props) {
  return getApiResponse(USER_ENDPOINT)
    .then(response => handleApiResponse(USER_ENDPOINT, response))
    .then(data => {
      props.setUser(data);
      props.setError(null);
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
      headers: createPostHeaders({ 'Content-Type': 'application/json' }),
      credentials: 'include'
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
    // X-Xsrf-Token value is now set to a constant matching the policy configuration in PingAccess. This combined with
    // the SameSite attribute set on the session cookie provides CSRF protection.
    'X-Xsrf-Token': 'constant-value'
  }, headers);
}

function getApiResponse(endpoint) {
  return fetch(endpoint, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'X-Xsrf-Token': 'constant-value'
    },
    credentials: 'include'
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

  if (contentType === 'application/json; charset=UTF-8' || contentType === 'application/json') {
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
