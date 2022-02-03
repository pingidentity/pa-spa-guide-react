import React from "react";
import ReactDOM from "react-dom";
import App from "./App";
import preset from "@rebass/preset";
import { ThemeProvider } from "emotion-theming";

const theme = {
  ...preset
};
ReactDOM.render(
  <ThemeProvider theme={{
    buttons: {
      primary: {
        backgroundColor: '#07c',
        '&:hover': {
          backgroundColor: 'darkblue'
        }
      },
      outline: {
        color: 'primary',
        '&:hover': {
          color: 'darkblue'
        },
        bg: 'transparent',
        boxShadow: 'inset 0 0 0 2px'
      }
    }, theme
  }}>
    <App/>
  </ThemeProvider>, document.getElementById("root")
);
