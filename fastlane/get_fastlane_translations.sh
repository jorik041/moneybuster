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

git add .
