#!/bin/sh

usage() {
    test $# -gt 0 && echo "error: $*" >&2
    cat >&2 <<EOF
usage: $0 [-a USER:PASS] -u http://SERVER[:PORT] [-v] [COMMAND [ARGS...]]

A simple command-line client for testing the signman HTTP API.

commands:
    status
        Show JSON status data. Authentication not needed.
    update FOREGROUND BACKGROUND MESSAGE
        Update the message and colors. Authentication
        required. Foreground and background colors should be 6-digit
        hex RGB values or numeric color indexes, depending on server
        configuration.
EOF
    exit 1
}

curl_args="--no-progress-meter --fail"
auth_args=
auth_val=
url=
while getopts a:hu:v opt; do
    case "$opt" in
        a) auth_args="--anyauth --user"; auth_val="$OPTARG" ;;
        u) url="$OPTARG" ;;
        v) curl_args="$curl_args --verbose" ;;
        *) usage ;;
    esac
done
shift $(($OPTIND - 1))

test -z "$url" && usage "missing -u argument"

run_curl() {
    local path
    path=$1
    shift
    if [ -n "$auth_val" ]; then
        curl $curl_args $auth_args "$auth_val" "$@" "$url$path"
    else
        curl $curl_args "$@" "$url$path"
    fi
}

find_json_formatter() {
    if echo {} | json_reformat >/dev/null 2>&1; then
        format_json=json_reformat
    else
        format_json=cat
    fi
}

case "$1" in
    ''|status)
        test $# -gt 1 && usage "extra arguments for status command"
        find_json_formatter
        run_curl /api/v1/status | $format_json
    ;;

    update)
        test $# -lt 4 && usage "missing arguments for $1 command"
        fg=$2
        bg=$3
        shift 3
        text="$(echo "$@" | sed 's/"/\\"/g')"
        if [ ${#fg} = 6 ]; then fg=\""$fg"\"; fi
        if [ ${#bg} = 6 ]; then fg=\""$bg"\"; fi
        run_curl /api/v1/update --json "{\"text\":\"$text\",\"fg\":$fg,\"bg\":$bg}"
    ;;

    *)
        usage "unknown command: $*"
        ;;
esac
