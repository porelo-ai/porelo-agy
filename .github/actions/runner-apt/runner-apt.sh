# shellcheck shell=bash
# The runner's distribution packages by way of the Actions cache, sourced by the steps of this action. The list of what
# to install is resolved from fresh, signed apt metadata on every run; the .deb files themselves are kept in the cache
# under a key that holds the hash of that list, so a run on an unchanged list reaches no package mirror. Every file,
# from the cache or just fetched, is checked against the SHA512 or SHA256 the metadata names, and once more right before
# it is installed: apt itself checks only the size of a file it finds in its archive. What the functions say goes to
# standard error.

runner_apt_sudo() { # runs a command as root, through sudo where the step is not root already
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    else
        sudo "$@"
    fi
}

runner_apt_pins() { # reads `apt-get install -s` on standard input: one name:arch=version for each package it installs;
    # fails on an Inst line it cannot read, which would otherwise be a package silently left out
    local line pattern='^Inst ([^ ]+) (\[[^]]*\] )?\(([^ ]+) [^)]*\[([^]]+)\]\)( \[[^]]*\])?$'
    while IFS= read -r line; do
        case $line in
            'Inst '*)
                if ! [[ $line =~ $pattern ]]; then
                    echo "::error::apt simulates an install this cannot read: '$line'" >&2
                    return 1
                fi
                echo "${BASH_REMATCH[1]}:${BASH_REMATCH[4]}=${BASH_REMATCH[3]}"
                ;;
        esac
    done
}

runner_apt_entry() { # <pin>: reads `apt-get download --print-uris` of that pin on standard input; prints its
    # manifest line, <pin> <file> <size> <algorithm>:<hash>, or fails on anything it cannot vouch for
    local pin=$1 line file size hash count=0
    while read -r line; do
        [ -n "$line" ] || continue
        read -r _ file size hash <<< "$line"
        count=$((count + 1))
        # The name is joined to a directory and handed to apt, so it is a plain file name and nothing else.
        if ! [[ $file =~ ^[A-Za-z0-9][A-Za-z0-9.+~%_-]*\.deb$ ]] || ! [[ $size =~ ^[0-9]+$ ]]; then
            echo "::error::apt names a file '$file' of size '$size' for $pin" >&2
            return 1
        fi
        if ! [[ $hash =~ ^SHA512:[0-9a-f]{128}$ || $hash =~ ^SHA256:[0-9a-f]{64}$ ]]; then
            echo "::error::apt names no SHA512 or SHA256 for $file, only '${hash%%:*}'" >&2
            return 1
        fi
        echo "$pin $file $size $hash"
    done
    if [ "$count" -ne 1 ]; then
        echo "::error::apt names $count files for $pin, not one" >&2
        return 1
    fi
}

runner_apt_image() { # the runner's image, as GitHub names it, else the distribution and its version
    local image
    if [ -n "${ImageOS:-}" ] && [ -n "${ImageVersion:-}" ]; then
        image="$ImageOS-$ImageVersion"
    else
        # shellcheck source=/dev/null
        image=$(. /etc/os-release && echo "$ID-$VERSION_ID")
    fi
    echo "${image//[^A-Za-z0-9._-]/_}"
}

runner_apt_resolve() { # <manifest> <package>...: writes what installing the packages takes, from fresh metadata
    local manifest=$1 simulated pins pin uris
    shift
    runner_apt_sudo apt-get update -qq || return 1
    # Each answer is taken whole and checked, never read from a process substitution, whose failure bash ignores.
    simulated=$(apt-get install -s -qq --no-install-recommends "$@") || return 1
    pins=$(runner_apt_pins <<< "$simulated") || return 1
    if [ -z "$pins" ]; then
        echo "::error::apt installs nothing for $*, which were found missing" >&2
        return 1
    fi
    : > "$manifest.part"
    while read -r pin; do
        uris=$(apt-get download --print-uris "$pin") || return 1
        runner_apt_entry "$pin" <<< "$uris" >> "$manifest.part" || return 1
    done <<< "$pins"
    sort -o "$manifest" "$manifest.part" && rm -f "$manifest.part"
}

runner_apt_matches() { # <file> <algorithm>:<hash>: succeeds when the file is a plain file with that hash
    local file=$1 algorithm=${2%%:*} hash=${2#*:} sum
    [ -f "$file" ] && [ ! -L "$file" ] || return 1
    case $algorithm in
        SHA512) sum=$(sha512sum "$file") ;;
        SHA256) sum=$(sha256sum "$file") ;;
        *) return 1 ;;
    esac
    [ "${sum%% *}" = "$hash" ]
}

runner_apt_check() { # <manifest> <dir>: keeps each file that matches, removes everything else in the directory;
    # prints the pins of what is missing then
    local manifest=$1 dir=$2 pin file size hash entry known
    # A link would carry what is written into the directory somewhere else.
    if [ -L "$dir" ]; then
        rm -f "$dir"
    fi
    mkdir -p "$dir" || return 1
    while read -r pin file size hash; do
        if runner_apt_matches "$dir/$file" "$hash"; then
            echo "$file taken from the cache, checked against its ${hash%%:*}" >&2
            continue
        fi
        if [ -e "$dir/$file" ] || [ -L "$dir/$file" ]; then
            echo "::warning::$file in the cache does not have the ${hash%%:*} apt names; refused and fetched again" >&2
            rm -f "$dir/$file"
        fi
        echo "$pin"
    done < "$manifest"
    # What no manifest line names, apt's lock and partial directory among it, would only grow the entry or lead apt
    # elsewhere through a link.
    while IFS= read -r -d '' entry; do
        known=no
        while read -r pin file size hash; do
            [ "$file" != "${entry##*/}" ] || known=yes
        done < "$manifest"
        [ "$known" = yes ] || runner_apt_sudo rm -rf -- "$entry"
    done < <(find "$dir" -mindepth 1 -maxdepth 1 -print0)
}

runner_apt_fetch() { # <manifest> <dir>: every file the manifest names in the directory, checked
    local manifest=$1 dir=$2 missing=() pin pins
    pins=$(runner_apt_check "$manifest" "$dir") || return 1
    while read -r pin; do
        [ -z "$pin" ] || missing+=("$pin")
    done <<< "$pins"
    if [ ${#missing[@]} -gt 0 ]; then
        echo "fetching ${#missing[@]} package(s) the cache lacks" >&2
        (cd "$dir" && pinned_retry "the runner's packages" apt-get download -qq "${missing[@]}") || return 1
    fi
    runner_apt_complete "$manifest" "$dir"
}

runner_apt_complete() { # <manifest> <dir>: succeeds when every file the manifest names is there with its hash
    local manifest=$1 dir=$2 pin file size hash bad=0
    while read -r pin file size hash; do
        if ! runner_apt_matches "$dir/$file" "$hash"; then
            echo "::error::$file does not have the ${hash%%:*} apt names" >&2
            rm -f "$dir/$file"
            bad=1
        fi
    done < "$manifest"
    return "$bad"
}

runner_apt_install() { # <manifest> <dir>: installs the pinned packages from the directory alone, never a mirror,
    # each file checked again first
    local manifest=$1 dir=$2 pins=() pin file size hash
    while read -r pin file size hash; do
        pins+=("$pin")
    done < "$manifest"
    if [ ${#pins[@]} -eq 0 ]; then
        echo "::error::the manifest names no package" >&2
        return 1
    fi
    runner_apt_complete "$manifest" "$dir" || return 1
    # apt reads a relative archive directory against its own cache directory, not the working directory.
    dir=$(cd "$dir" && pwd -P) || return 1
    mkdir -p "$dir/partial"
    runner_apt_sudo apt-get install -y -qq --no-install-recommends --no-download \
        -o Dir::Cache::archives="$dir/" "${pins[@]}"
}
