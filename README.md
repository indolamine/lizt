# Lizt

A proof-of-concept app, demonstrating a way to create shared state between a backend and a number of frontends via a CRDT. 
This repo will have a corresponding screencast series as well (tbd). 

## Usage

1. Clone the repo
2. Download npm dependencies
``` 
npm install
```
3. Build the frontend app
```
npx shadow-cljs release app
```
4. Launch the server
```
clojure -M -m lizt.core
```

5. Navigate to http://localhost:8890/index.html , on multiple browsers/tabs

## For development

1. Use `npx shadow-cljs watch app` for a continuous cljs->js compilation and browser hot-loading
2. This will open an nrepl for cljs on port 7002
3. Launch the server in a clj repl session from the `lizt.core` namespace via `(dev-start)` . Stop with `(dev-stop)`
