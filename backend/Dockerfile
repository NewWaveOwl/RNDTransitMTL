FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY --chown=node:node . .
USER node
EXPOSE 18088
CMD ["node", "server.mjs"]
