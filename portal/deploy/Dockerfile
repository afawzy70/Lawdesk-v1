# The portal image: static files only, unprivileged nginx on 8080. Build context = a directory holding dist/ (the
# output of portal/tools/build_portal.py) and this folder's nginx files; see portal/deploy/scripts/deploy.sh.
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY snippets/ /etc/nginx/snippets/
COPY dist/ /usr/share/nginx/html/
