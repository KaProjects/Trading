FROM node:23.11.0-alpine3.21 as build-stage
WORKDIR /app
COPY . /app
ARG REACT_APP_BACKEND_URL='http://127.0.0.1:8080'
RUN npm install
RUN npm run build

FROM nginx:1.27.5-alpine3.21 as production-stage
COPY --from=build-stage /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
CMD ["nginx", "-g", "daemon off;"]
