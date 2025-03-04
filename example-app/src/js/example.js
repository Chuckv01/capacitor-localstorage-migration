import { LocalStorageMigration } from 'capacitor-localstorage-migration';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    LocalStorageMigration.echo({ value: inputValue })
}
