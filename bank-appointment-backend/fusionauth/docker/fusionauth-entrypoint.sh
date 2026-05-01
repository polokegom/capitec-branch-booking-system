#!/usr/bin/env sh
set -eu

template_file="${FUSIONAUTH_KICKSTART_TEMPLATE:-/usr/local/fusionauth/kickstart/kickstart.json.template}"
kickstart_file="${FUSIONAUTH_APP_KICKSTART_FILE:-/usr/local/fusionauth/kickstart/kickstart.json}"
verification_html_file="${FUSIONAUTH_VERIFICATION_HTML_TEMPLATE:-/usr/local/fusionauth/kickstart/emailVerificationTemplate.html}"
allowed_origin="${FUSIONAUTH_ALLOWED_ORIGIN:-http://localhost:4200}"
public_base_url="${FUSIONAUTH_PUBLIC_BASE_URL:-${allowed_origin}}"
issuer="${FUSIONAUTH_ISSUER:-http://localhost:9011}"
redirect_url="${FUSIONAUTH_REDIRECT_URL:-${allowed_origin}/auth/callback}"
logout_url="${FUSIONAUTH_LOGOUT_URL:-${allowed_origin}/}"
admin_email="${FUSIONAUTH_APP_DEFAULT_ADMIN_USERNAME:-admin@capitec-booking.co.za}"
admin_password="${FUSIONAUTH_APP_DEFAULT_ADMIN_PASSWORD:-password123}"
api_key="${FUSIONAUTH_API_KEY:-bf69486b-4733-4470-a592-f1bfce7af580}"
application_id="${FUSIONAUTH_APPLICATION_ID:-85a03867-dccf-4882-adde-1a79aeec50df}"
smtp_host="${FUSIONAUTH_SMTP_HOST:-mailhog}"
smtp_port="${FUSIONAUTH_SMTP_PORT:-1025}"
smtp_security="${FUSIONAUTH_SMTP_SECURITY:-NONE}"
smtp_username="${FUSIONAUTH_SMTP_USERNAME:-}"
smtp_password="${FUSIONAUTH_SMTP_PASSWORD:-}"
mail_from="${FUSIONAUTH_MAIL_FROM:-no-reply@capitec-booking.co.za}"
email_header_image_url="${FUSIONAUTH_EMAIL_HEADER_IMAGE_URL:-${EMAIL_ASSETS_BASE_URL}/capitec_background_img_email.jpeg}"
email_verify_base_url="${FUSIONAUTH_EMAIL_VERIFY_BASE_URL:-${public_base_url}/api/v1/auth/verify-email}"

escape_sed_replacement() {
  printf '%s' "$1" | sed -e 's/[\\&|]/\\&/g'
}

json_escape_file() {
  awk '
    BEGIN { first = 1 }
    {
      gsub(/\\/, "\\\\")
      gsub(/"/, "\\\"")
      if (!first) {
        printf "\\n"
      }
      printf "%s", $0
      first = 0
    }
  ' "$1"
}

allowed_origin_escaped="$(escape_sed_replacement "$allowed_origin")"
issuer_escaped="$(escape_sed_replacement "$issuer")"
redirect_url_escaped="$(escape_sed_replacement "$redirect_url")"
logout_url_escaped="$(escape_sed_replacement "$logout_url")"
admin_email_escaped="$(escape_sed_replacement "$admin_email")"
admin_password_escaped="$(escape_sed_replacement "$admin_password")"
api_key_escaped="$(escape_sed_replacement "$api_key")"
application_id_escaped="$(escape_sed_replacement "$application_id")"
smtp_host_escaped="$(escape_sed_replacement "$smtp_host")"
smtp_port_escaped="$(escape_sed_replacement "$smtp_port")"
smtp_security_escaped="$(escape_sed_replacement "$smtp_security")"
smtp_username_escaped="$(escape_sed_replacement "$smtp_username")"
smtp_password_escaped="$(escape_sed_replacement "$smtp_password")"
mail_from_escaped="$(escape_sed_replacement "$mail_from")"
prepared_verification_html="$(mktemp)"
sed \
  -e "s|__EMAIL_HEADER_IMAGE_SRC__|$(escape_sed_replacement "$email_header_image_url")|g" \
  -e "s|__EMAIL_VERIFY_BASE_URL__|$(escape_sed_replacement "$email_verify_base_url")|g" \
  "$verification_html_file" > "$prepared_verification_html"
verification_html_escaped="$(escape_sed_replacement "$(json_escape_file "$prepared_verification_html")")"

mkdir -p "$(dirname "$kickstart_file")"

sed \
  -e "s|http://localhost:4200/auth/callback|${redirect_url_escaped}|g" \
  -e "s|http://localhost:4200/|${logout_url_escaped}|g" \
  -e "s|http://localhost:4200|${allowed_origin_escaped}|g" \
  -e "s|http://localhost:9011|${issuer_escaped}|g" \
  -e "s|admin@capitec-booking.co.za|${admin_email_escaped}|g" \
  -e "s|\"adminPassword\": \"password123\"|\"adminPassword\": \"${admin_password_escaped}\"|g" \
  -e "s|bf69486b-4733-4470-a592-f1bfce7af580|${api_key_escaped}|g" \
  -e "s|85a03867-dccf-4882-adde-1a79aeec50df|${application_id_escaped}|g" \
  -e "s|no-reply@capitec-booking.co.za|${mail_from_escaped}|g" \
  -e "s|\"host\": \"mailhog\"|\"host\": \"${smtp_host_escaped}\"|g" \
  -e "s|\"port\": 1025|\"port\": ${smtp_port_escaped}|g" \
  -e "s|\"security\": \"NONE\"|\"security\": \"${smtp_security_escaped}\"|g" \
  -e "s|__SMTP_USERNAME__|${smtp_username_escaped}|g" \
  -e "s|__SMTP_PASSWORD__|${smtp_password_escaped}|g" \
  -e "s|__EMAIL_VERIFY_BASE_URL__|$(escape_sed_replacement "$email_verify_base_url")|g" \
  -e "s|__VERIFICATION_HTML_TEMPLATE__|${verification_html_escaped}|g" \
  "$template_file" > "$kickstart_file"

rm -f "$prepared_verification_html"

exec "$@"
