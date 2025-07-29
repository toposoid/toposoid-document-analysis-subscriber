# toposoid-knowledge-register-subscriber
This is a subscriber that works as a microservice within the Toposoid project.
Toposoid is a knowledge base construction platform.(see [Toposoid　Root Project](https://github.com/toposoid/toposoid.git))
This microservice provides the functionality to register from a document.


## Requirements
Scala version 2.13.x,   
Sbt version 1.4.9.

## Recommended Environment For Standalone
* Required: at least 8GB of RAM.
* Required: at least 1.33G of HDD(Docker Image Size)

## Setup
```
sbt　publishLocal
```
* This library uses The PDF Extract API. In order to use The PDF Extract API, you need to create credentials. https://developer.adobe.com/document-services/apis/pdf-services/
  After creating the credentials, please set the following environment variables in docker-compose.yml.
  TOPOSOID_PDF_SERVICES_CLIENT_ID
  TOPOSOID_PDF_SERVICES_CLIENT_SECRET


## Usage
```bash
curl -X POST localhost:9012/uploadDocumentFile -H 'X_TOPOSOID_TRANSVERSAL_STATE: {"userId":"test-user", "username":"guest", "roleId":0, "csrfToken":""}' -F "uploadfile=@YourFilePath;type=application/octet-stream"
```
## Note

## License
This program is offered under a commercial and under the AGPL license.
For commercial licensing, contact us at https://toposoid.com/contact.  For AGPL licensing, see below.

AGPL licensing:
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

## Author
* Makoto Kubodera([Linked Ideal LLC.](https://linked-ideal.com/))

Thank you!