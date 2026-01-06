root="$(cd "$(dirname "$0")/.." && pwd)"
vers="$(sed -n 's/^ *version *= *"\([^"]*\)" *$/\1/p' < "$root/build.gradle.kts")"
name="signman-$vers"
dist="$root/dist"
build="$dist/build"
dest="$build/$name"

set -e
set -x

rm -rf "$build"
mkdir -p "$dest/bin" "$dest/lib" "$dest/examples"

cp "$root/cli/build/scriptsShadow/signman-cli" "$dest/bin"
cp "$root/server/build/scriptsShadow/signman-server" "$dest/bin"

cp "$root/cli/build/libs/signman-"*.jar "$dest/lib"
cp "$root/server/build/libs/signman-"*.jar "$dest/lib"

make -C "$root/server/native" clean
cp -r "$root/server/native" "$dest/native"

cp "$root/examples/"* "$dest/examples"
cp "$dist/"*.socket "$dist/"*.service "$dist/INSTALL" "$dest/"
cp "$dist/dist.mk" "$dest/Makefile"

tar -C "$build" -czf "$build/${name}.tar.gz" "$name"
