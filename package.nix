{
  lib,
  version,
  buildGradleApplication,
  jdk,
  ...
}:

buildGradleApplication {
  pname = "tcs-bot";
  version = version;
  src = ./.;

  inherit jdk;

  dependencyFilter = depSpec: depSpec.name != "${depSpec.component.name}-metadata-${depSpec.component.version}.jar";

  meta = with lib; {
    description = "Discord bot that verifies members of unofficial TCS Discord servers.";

    sourceProvenance = with sourceTypes; [
      fromSource
      binaryBytecode
    ];

    platforms = platforms.unix;
  };
}
