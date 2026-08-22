#!/bin/sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH='' cd -- "$script_dir/../.." && pwd)
node_version=$(tr -d '[:space:]' < "$repo_root/jacodb-ets/ts-frontend/.nvmrc")

case "$node_version" in
    20.20.2)
        node_sha256=bb8a5273607ebe712a27bb4f8870fb1257d09993b8d0812a4178a3feaa2effa4
        ;;
    *)
        echo "No checksum configured for Node.js $node_version" >&2
        exit 1
        ;;
esac

if [ "$(uname -s)" != Linux ] || [ "$(uname -m)" != x86_64 ]; then
    echo "The JitPack Node.js bootstrap supports Linux x86_64 only" >&2
    exit 1
fi

install_root=${JITPACK_NODE_INSTALL_ROOT:-"$repo_root/.jitpack"}
archive="node-v${node_version}-linux-x64-glibc-217.tar.xz"
archive_path="$install_root/$archive"
extracted_dir="$install_root/${archive%.tar.xz}"
node_dir="$install_root/node"
download_url="https://unofficial-builds.nodejs.org/download/release/v${node_version}/$archive"

mkdir -p "$install_root"
curl --fail --location --show-error --silent \
    --retry 3 --retry-delay 1 \
    --output "$archive_path" \
    "$download_url"
printf '%s  %s\n' "$node_sha256" "$archive_path" | sha256sum --check
tar -xJf "$archive_path" -C "$install_root"
ln -s "$extracted_dir" "$node_dir"

if [ ! -x "$node_dir/bin/node" ] || [ ! -x "$node_dir/bin/npm" ]; then
    echo "The downloaded Node.js toolchain is incomplete" >&2
    exit 1
fi

"$node_dir/bin/node" --version
"$node_dir/bin/npm" --version
