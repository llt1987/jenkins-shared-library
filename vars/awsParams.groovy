def awsSecretParameter(){
      return [
            [
                  $class: 'StringParameterDefinition',
                  name: 'AWS_ACCESS_KEY_ID',
                  defaultValue: 'Enter AWS ACCESS KEY',
		],
		[
                  $class: 'StringParameterDefinition',
                  name: 'AWS_SECRET_ACCESS_KEY',
                  defaultValue: 'Enter AWS SECRET KEY',
		],
		[
                  $class: 'StringParameterDefinition',
                  name: 'AWS_SESSION_TOKEN',
                  defaultValue: 'Enter AWS SESSION TOKEN',
		]
      ]
}