# syntax=docker/dockerfile:1.7
FROM fusionauth/fusionauth-app:1.55.1

USER root

COPY --chown=fusionauth:fusionauth fusionauth/kickstart/kickstart.json /usr/local/fusionauth/kickstart/kickstart.json.template
COPY --chown=fusionauth:fusionauth src/main/resources/email/emailVerificationTemplate.html /usr/local/fusionauth/kickstart/emailVerificationTemplate.html
COPY --chown=fusionauth:fusionauth fusionauth/docker/fusionauth-entrypoint.sh /usr/local/bin/fusionauth-entrypoint.sh

RUN chmod +x /usr/local/bin/fusionauth-entrypoint.sh

USER fusionauth

ENTRYPOINT ["/usr/local/bin/fusionauth-entrypoint.sh"]
CMD ["/usr/local/fusionauth/fusionauth-app/bin/start.sh"]
