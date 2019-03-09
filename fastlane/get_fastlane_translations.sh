#!/bin/bash

test -d metadata/android/fr-FR || mkdir metadata/android/fr-FR
cp ../app/src/main/res/fr/moneybuster_fastlane/full_description.txt metadata/android/fr-FR/full_description.txt
cp ../app/src/main/res/fr/moneybuster_fastlane/short_description.txt metadata/android/fr-FR/short_description.txt
echo "MoneyBuster" > metadata/android/fr-FR/title.txt

test -d metadata/android/de-DE || mkdir metadata/android/de-DE
cp ../app/src/main/res/de/moneybuster_fastlane/full_description.txt metadata/android/de-DE/full_description.txt
cp ../app/src/main/res/de/moneybuster_fastlane/short_description.txt metadata/android/de-DE/short_description.txt
echo "MoneyBuster" > metadata/android/de-DE/title.txt

test -d metadata/android/es-ES || mkdir metadata/android/es-ES
cp ../app/src/main/res/es-ES/moneybuster_fastlane/full_description.txt metadata/android/es-ES/full_description.txt
cp ../app/src/main/res/es-ES/moneybuster_fastlane/short_description.txt metadata/android/es-ES/short_description.txt
echo "MoneyBuster" > metadata/android/es-ES/title.txt

test -d metadata/android/it-IT || mkdir metadata/android/it-IT
cp ../app/src/main/res/it/moneybuster_fastlane/full_description.txt metadata/android/it-IT/full_description.txt
cp ../app/src/main/res/it/moneybuster_fastlane/short_description.txt metadata/android/it-IT/short_description.txt
echo "MoneyBuster" > metadata/android/it-IT/title.txt

test -d metadata/android/zh-CN || mkdir metadata/android/zh-CN
cp ../app/src/main/res/zh-CN/moneybuster_fastlane/full_description.txt metadata/android/zh-CN/full_description.txt
cp ../app/src/main/res/zh-CN/moneybuster_fastlane/short_description.txt metadata/android/zh-CN/short_description.txt
echo "MoneyBuster" > metadata/android/zh-CN/title.txt

test -d metadata/android/pt-BR || mkdir metadata/android/pt-BR
cp ../app/src/main/res/pt-BR/moneybuster_fastlane/full_description.txt metadata/android/pt-BR/full_description.txt
cp ../app/src/main/res/pt-BR/moneybuster_fastlane/short_description.txt metadata/android/pt-BR/short_description.txt
echo "MoneyBuster" > metadata/android/pt-BR/title.txt

test -d metadata/android/nl-NL || mkdir metadata/android/nl-NL
cp ../app/src/main/res/nl/moneybuster_fastlane/full_description.txt metadata/android/nl-NL/full_description.txt
cp ../app/src/main/res/nl/moneybuster_fastlane/short_description.txt metadata/android/nl-NL/short_description.txt
echo "MoneyBuster" > metadata/android/nl-NL/title.txt

git add .
