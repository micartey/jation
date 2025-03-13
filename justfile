bump version:
    @echo "Bumping version to {{ version }}"
    sed -i -E 's/implementation "me\.micartey:jation:[0-9]+\.[0-9]+\.[0-9]+"/implementation "me.micartey:jation:{{ version }}"/g' README.md
    sed -i -E 's/version \= "[0-9]+\.[0-9]+\.[0-9]+"/version \= "{{ version }}"/g' build.gradle
